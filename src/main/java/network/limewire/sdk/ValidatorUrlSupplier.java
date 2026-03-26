package network.limewire.sdk;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Marker interface for a supplier of URLs that will be used as source endpoints of validator instances.
 */
public interface ValidatorUrlSupplier extends Supplier<CompletableFuture<URL>> {
}
