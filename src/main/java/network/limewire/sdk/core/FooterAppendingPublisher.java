package network.limewire.sdk.core;

import network.limewire.sdk.footer.FooterSession;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkHttpContentPublisher;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Wraps a {@code Publisher<ByteBuffer>} to append a LimeWireNetwork footer with chunk signatures.
 * This is the async equivalent of {@link FooterAppendingInputStream}.
 */
public class FooterAppendingPublisher implements SdkHttpContentPublisher {
    private final Publisher<ByteBuffer> upstream;
    private final AsyncExecuteRequest request;

    private final FooterSession footerSession;

    public FooterAppendingPublisher(AsyncExecuteRequest request,
                                    Publisher<ByteBuffer> upstream,
                                    FooterSession footerSession) {
        this.request = request;
        this.upstream = upstream;
        this.footerSession = footerSession;
    }

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> downstream) {
        if (hasRequestBody(request.request())) {
            upstream.subscribe(new FooterAppendingSubscriber(downstream, footerSession));
        } else {
            upstream.subscribe(downstream);
        }
    }

    private boolean hasRequestBody(SdkHttpRequest originalRequest) {
        Optional<String> contentLengthHeader = originalRequest.firstMatchingHeader("Content-Length");
        Optional<String> transferEncodingHeader = originalRequest.firstMatchingHeader("Transfer-Encoding");

        return contentLengthHeader.isPresent() || transferEncodingHeader.isPresent();
    }

    @Override
    public Optional<Long> contentLength() {
        return Optional.empty();
    }

    private static class FooterAppendingSubscriber implements Subscriber<ByteBuffer> {
        private static final Logger logger = LoggerFactory.getLogger(FooterAppendingSubscriber.class);

        private final Subscriber<? super ByteBuffer> downstream;
        private Subscription upstream;
        private final byte[] buffer;
        private int bufferPos = 0;
        private boolean completed = false;

        private final FooterSession footerSession;

        FooterAppendingSubscriber(Subscriber<? super ByteBuffer> downstream, FooterSession footerSession) {
            this.footerSession = footerSession;
            this.downstream = downstream;
            this.buffer = new byte[footerSession.chunkSize()];
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.upstream = s;
            this.downstream.onSubscribe(s);
        }

        @Override
        public void onNext(ByteBuffer byteBuffer) {
            try {
                while (byteBuffer.hasRemaining()) {
                    int available = footerSession.chunkSize() - bufferPos;
                    int toRead = Math.min(available, byteBuffer.remaining());
                    byteBuffer.get(buffer, bufferPos, toRead);
                    bufferPos += toRead;

                    if (bufferPos == footerSession.chunkSize()) {
                        byte[] chunk = Arrays.copyOf(buffer, footerSession.chunkSize());
                        footerSession.onBytes(chunk);
                        bufferPos = 0;
                    }
                }

                byteBuffer.rewind();
                this.downstream.onNext(byteBuffer);

            } catch (Exception e) {
                this.upstream.cancel();
                this.downstream.onError(e);
            }
        }

        @Override
        public void onError(Throwable t) {
            this.downstream.onError(t);
        }

        @Override
        public void onComplete() {
            if (this.completed) {
                return;
            }

            this.completed = true;

            try {
                if (this.bufferPos > 0) {
                    byte[] lastChunk = Arrays.copyOf(this.buffer, this.bufferPos);
                    this.footerSession.onBytes(lastChunk);
                }

                footerSession.finish();
                byte[] footer = footerSession.footerBytes();

                logger.debug("Generated Footer with {} bytes", footer.length);

                this.downstream.onNext(ByteBuffer.wrap(footer));
                this.downstream.onComplete();
            } catch (Exception e) {
                this.downstream.onError(e);
            }
        }
    }
}
