package network.limewire.sdk.core;

/**
 * Strategy interface for generating request IDs used in headers.
 */
public interface RequestIdGenerator {
    String generateId();
}
