package network.limewire.sdk;

import network.limewire.sdk.core.FooterAppendingPublisher;
import network.limewire.sdk.core.LimeWireNetworkSigner;
import network.limewire.sdk.core.RequestIdGenerator;
import network.limewire.sdk.footer.BinaryFooterCodec;
import network.limewire.sdk.footer.FooterOptions;
import network.limewire.sdk.footer.FooterSession;
import network.limewire.sdk.footer.FooterSessionFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpResponseHandler;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A wrapper around an {@link SdkAsyncHttpClient} that appends the LimeWireNetwork footer to request bodies
 * and adds LimeWireNetwork headers (x-lmwrntwrk-request-id, x-lmwrntwrk-Signature) using the final
 * AWS-signed Authorization header that is only available at the HTTP client layer.
 */
public final class LimeWireNetworkAsyncHttpClient implements SdkAsyncHttpClient {
    private final SdkAsyncHttpClient delegate;
    private final ValidatorUrlSupplier validatorUrlSupplier;
    private final ValidatorEventPublisher eventPublisher;

    private final FooterSessionFactory factory;
    private final LimeWireNetworkRequestEnricher enricher;

    private final ValidatorEventPayloadGenerator payloadGenerator = new ValidatorEventPayloadGenerator();

    public LimeWireNetworkAsyncHttpClient(SdkAsyncHttpClient delegate,
                                          FooterOptions footerOptions,
                                          LimeWireNetworkSigner signer,
                                          RequestIdGenerator requestIdGen,
                                          ValidatorUrlSupplier validatorUrlSupplier,
                                          ValidatorEventPublisher validatorEventPublisher) {
        this.delegate = delegate;
        this.validatorUrlSupplier = validatorUrlSupplier;
        this.eventPublisher = validatorEventPublisher;
        this.factory = new FooterSessionFactory(signer, BinaryFooterCodec.V1, footerOptions);
        this.enricher = new LimeWireNetworkRequestEnricher(requestIdGen, signer, footerOptions);
    }

    @Override
    public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
        SdkHttpFullRequest originalRequest = (SdkHttpFullRequest) request.request();

        String authHeader = originalRequest.firstMatchingHeader("Authorization")
                .filter(it -> !it.isEmpty())
                .orElse(null);

        if (authHeader == null) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            result.completeExceptionally(new IllegalStateException("Missing Authorization Header for LimeWireNetwork signing"));
            return result;
        }

        SdkHttpFullRequest updatedRequest = enricher.enrichHeaders(originalRequest, authHeader);

        FooterSession footerSession = factory.create();
        S3ActionResolver.S3Action s3Action = S3ActionResolver.resolve(updatedRequest);

        if (!s3Action.isAllowed()) {
            throw new IllegalArgumentException("Request of type '" + s3Action + "' is not allowed by LimeWireNetwork");
        }

        MaybeBufferingPublisher bufferingPublisher = new MaybeBufferingPublisher(request.requestContentPublisher(), s3Action.shouldBufferRequest());

        final ValidatorEventSendingContext context = new ValidatorEventSendingContext();
        context.setRequest(updatedRequest);
        context.setFooterSupplier(footerSession::validatorPayload);
        context.setBodySupplier(bufferingPublisher::getBody);
        context.setShouldBufferResponse(s3Action.shouldBufferResponse());

        AsyncExecuteRequest.Builder httpRequestBuilder = AsyncExecuteRequest.builder()
                .request(updatedRequest)
                .requestContentPublisher(new FooterAppendingPublisher(request, bufferingPublisher, footerSession))
                .responseHandler(new ValidatorSendingResponseHandler(request.responseHandler(), context));

        return delegate.execute(httpRequestBuilder.build());
    }

    private static class MaybeBufferingPublisher implements Publisher<ByteBuffer> {
        private final Publisher<ByteBuffer> delegate;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final boolean shouldBuffer;

        private MaybeBufferingPublisher(Publisher<ByteBuffer> delegate, boolean shouldBuffer) {
            this.delegate = delegate;
            this.shouldBuffer = shouldBuffer;
        }

        public byte[] getBody() {
            if (shouldBuffer) {
                return buffer.toByteArray();
            }
            return null;
        }

        @Override
        public void subscribe(Subscriber<? super ByteBuffer> downstream) {
            this.delegate.subscribe(new Subscriber<ByteBuffer>() {
                @Override
                public void onSubscribe(Subscription s) {
                    downstream.onSubscribe(s);
                }

                @Override
                public void onNext(ByteBuffer byteBuffer) {
                    appendToStream(byteBuffer);
                    downstream.onNext(byteBuffer);
                }

                private void appendToStream(ByteBuffer source) {
                    if (shouldBuffer) {
                        ByteBuffer target = source.duplicate();
                        byte[] chunk = new byte[target.remaining()];
                        target.get(chunk);
                        buffer.write(chunk, 0, chunk.length);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    downstream.onError(t);
                }

                @Override
                public void onComplete() {
                    downstream.onComplete();
                }
            });
        }
    }

    private class ValidatorSendingSubscriber implements Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final Subscriber<? super ByteBuffer> delegate;
        private final ValidatorEventSendingContext context;

        private ValidatorSendingSubscriber(Subscriber<? super ByteBuffer> delegate,
                                           ValidatorEventSendingContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.delegate.onSubscribe(s);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            appendToStream(byteBuffer);
            delegate.onNext(byteBuffer);
        }

        private void appendToStream(ByteBuffer source) {
            if (context.isShouldBufferResponse()) {
                ByteBuffer target = source.duplicate();
                byte[] chunk = new byte[target.remaining()];
                target.get(chunk);
                buffer.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable t) {
            this.delegate.onError(t);
        }

        @Override
        public void onComplete() {
            SdkHttpResponse httpResponse = context.getResponseRef();
            if (httpResponse == null) {
                this.delegate.onComplete();
                return;
            }

            Optional<String> optionalEventPayload = payloadGenerator.generate(
                    context.getBodySupplier().get(),
                    context.getRequest(),
                    httpResponse.headers(),
                    context.isShouldBufferResponse() ? buffer.toByteArray() : null,
                    context.getFooterSupplier().get()
            );
            if (optionalEventPayload.isPresent()) {
                validatorUrlSupplier.get()
                        .thenCompose(validatorUrl -> eventPublisher.publish(validatorUrl, optionalEventPayload.get()))
                        .whenComplete((result, error) -> this.delegate.onComplete());
            } else {
                this.delegate.onComplete();
            }
        }
    }

    private class ValidatorSendingResponseHandler implements SdkAsyncHttpResponseHandler {
        private final SdkAsyncHttpResponseHandler delegate;
        private final ValidatorEventSendingContext context;


        private ValidatorSendingResponseHandler(SdkAsyncHttpResponseHandler delegate,
                                                ValidatorEventSendingContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public void onHeaders(SdkHttpResponse headers) {
            this.context.setResponseRef(headers);
            this.delegate.onHeaders(headers);
        }

        @Override
        public void onStream(Publisher<ByteBuffer> stream) {
            this.delegate.onStream(it -> stream.subscribe(new ValidatorSendingSubscriber(it, context)));
        }

        @Override
        public void onError(Throwable error) {
            this.delegate.onError(error);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public String clientName() {
        return "LimeWireNetworkAsyncHttpClient-" + delegate.clientName();
    }

}