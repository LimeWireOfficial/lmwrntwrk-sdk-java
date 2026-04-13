package network.limewire.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class DefaultValidatorEventPublisher implements ValidatorEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(DefaultValidatorEventPublisher.class);

    private final ExecutorService executorService = new ThreadPoolExecutor(
            1, // core size
            4, // max threads
            30, TimeUnit.SECONDS, // idle timeout
            new ArrayBlockingQueue<>(1024),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    @Override
    public CompletableFuture<Void> publish(URL validatorUrl, String eventPayload) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            executorService.execute(() -> doSend(result, validatorUrl, eventPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            logger.warn("an error occurred while scheduling validator event: {}", ex.getMessage());
        }

        return result;
    }

    private void doSend(CompletableFuture<Void> result, URL validatorUrl, byte[] payload) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) validatorUrl.openConnection();
            String userAgent = conn.getRequestProperty("User-Agent");
            if (userAgent != null && !userAgent.isEmpty()) {
                userAgent += " " + LimeWireNetworkVersion.getUserAgent();
            } else {
                userAgent = LimeWireNetworkVersion.getUserAgent();
            }

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Content-Length", Integer.toString(payload.length));
            conn.setRequestProperty("User-Agent", userAgent);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                logger.debug("Sent to url {}: '{}'", validatorUrl, new String(payload, StandardCharsets.UTF_8));
            } else {
                logger.warn("Validator POST failed, code: {}", code);
            }

        } catch (Exception ex) {
            logger.warn("an error occurred while sending validator event: {}", ex.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            result.complete(null);
        }
    }
}
