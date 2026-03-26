package network.limewire.sdk;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

public interface ValidatorEventPublisher {
    CompletableFuture<Void> publish(URL validatorUrl, String eventPayload);
}
