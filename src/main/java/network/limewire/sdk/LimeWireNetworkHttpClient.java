package network.limewire.sdk;

import network.limewire.sdk.core.FooterAppendingInputStream;
import network.limewire.sdk.core.LimeWireNetworkSigner;
import network.limewire.sdk.core.RequestIdGenerator;
import network.limewire.sdk.footer.BinaryFooterCodec;
import network.limewire.sdk.footer.FooterOptions;
import network.limewire.sdk.footer.FooterSession;
import network.limewire.sdk.footer.FooterSessionFactory;
import software.amazon.awssdk.http.*;

import java.io.*;

/**
 * A wrapper around an {@link SdkHttpClient} that appends the LimeWireNetwork footer to request bodies
 * and adds LMWRNTWRK headers (x-lmwrntwrk-request-id, x-lmwrntwrk-Signature) using the final
 * AWS-signed Authorization header that is only available at the HTTP client layer.
 */
public final class LimeWireNetworkHttpClient implements SdkHttpClient {
    private final SdkHttpClient delegate;
    private final ValidatorUrlSupplier validatorUrlSupplier;
    private final ValidatorEventPublisher eventPublisher;

    private final FooterSessionFactory factory;
    private final LimeWireNetworkRequestEnricher enricher;

    private final ValidatorEventPayloadGenerator payloadGenerator = new ValidatorEventPayloadGenerator();


    public LimeWireNetworkHttpClient(SdkHttpClient delegate,
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
    public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
        SdkHttpFullRequest originalRequest = (SdkHttpFullRequest) request.httpRequest();

        String authHeader = originalRequest.firstMatchingHeader("Authorization")
                .filter(it -> !it.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Missing Authorization header for LimeWireNetwork signing"));

        SdkHttpFullRequest updatedRequest = enricher.enrichHeaders(originalRequest, authHeader);

        FooterSession footerSession = factory.create();
        S3ActionResolver.S3Action s3Action = S3ActionResolver.resolve(updatedRequest);

        final ValidatorEventSendingContext context = new ValidatorEventSendingContext();
        context.setRequest(updatedRequest);
        context.setFooterSupplier(footerSession::validatorPayload);
        context.setBodySupplier(() -> null);
        context.setShouldBufferResponse(s3Action.shouldBufferResponse());

        HttpExecuteRequest.Builder httpRequestBuilder = HttpExecuteRequest.builder().request(updatedRequest);
        request.contentStreamProvider().ifPresent(original -> {
            MaybeBufferingInputStream bufferingInputStream = new MaybeBufferingInputStream(original.newStream(), s3Action.shouldBufferRequest());
            context.setBodySupplier(bufferingInputStream::getBody);
            httpRequestBuilder.contentStreamProvider(() -> new FooterAppendingInputStream(bufferingInputStream, footerSession));
        });

        final BufferingExecutableHttpRequest delegateRequest = new BufferingExecutableHttpRequest(
                delegate.prepareRequest(httpRequestBuilder.build()),
                context.isShouldBufferResponse());

        return new ExecutableHttpRequest() {
            @Override
            public HttpExecuteResponse call() throws IOException {
                HttpExecuteResponse response = delegateRequest.call();

                payloadGenerator.generate(
                                context.getBodySupplier().get(),
                                context.getRequest(),
                                response.httpResponse().headers(),
                                delegateRequest.getResponseBody(),
                                context.getFooterSupplier().get())
                        .ifPresent(payload -> validatorUrlSupplier.get()
                                .thenCompose(validatorUrl -> eventPublisher.publish(validatorUrl, payload))
                                .join());

                return response;
            }

            @Override
            public void abort() {
                delegateRequest.abort();
            }
        };
    }

    static final class BufferingExecutableHttpRequest implements ExecutableHttpRequest {
        private final ExecutableHttpRequest delegate;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private final boolean shouldBufferResponse;


        BufferingExecutableHttpRequest(ExecutableHttpRequest delegate, boolean shouldBufferResponse) {
            this.delegate = delegate;
            this.shouldBufferResponse = shouldBufferResponse;
        }

        @Override
        public HttpExecuteResponse call() throws IOException {
            HttpExecuteResponse resp = delegate.call();

            if (!resp.responseBody().isPresent()) {
                return resp;
            }

            try (InputStream original = resp.responseBody().get()) {
                copyToOutputStream(original);
            }

            AbortableInputStream replayableInputStream = AbortableInputStream.create(
                    new ByteArrayInputStream(buffer.toByteArray()));

            return HttpExecuteResponse.builder()
                    .response(resp.httpResponse())
                    .responseBody(replayableInputStream)
                    .build();
        }

        @Override
        public void abort() {
            delegate.abort();
        }

        public byte[] getResponseBody() {
            if (!this.shouldBufferResponse) {
                return null;
            }

            return buffer.toByteArray();
        }

        private void copyToOutputStream(InputStream in) throws IOException {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                this.buffer.write(buf, 0, n);
            }
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static class MaybeBufferingInputStream extends FilterInputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final boolean shouldBuffer;

        private MaybeBufferingInputStream(InputStream delegate, boolean shouldBuffer) {
            super(delegate);
            this.shouldBuffer = shouldBuffer;
        }

        @Override
        public int read() throws IOException {
            int n = super.read();
            if (n > 0 && this.shouldBuffer) {
                this.buffer.write(n);
            }

            return n;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0 && this.shouldBuffer) {
                this.buffer.write(b, off, n);
            }
            return n;
        }

        public byte[] getBody() {
            if (this.shouldBuffer) {
                return buffer.toByteArray();
            }

            return null;
        }
    }
}
