package network.limewire.sdk.core;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Default implementation of {@link RequestIdGenerator}, generating a monotonic ULID, mirroring the Go client's
 * ulid.Monotonic behavior
 */
public class DefaultRequestIdGenerator implements RequestIdGenerator {
    @Override
    public String generateId() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
