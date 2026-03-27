package network.limewire.sdk.core;

import network.limewire.sdk.LimeWireNetworkPrivateKeyFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * {@link LimeWireNetworkSigner} defines signing operations used by the SDK.
 * Implementations should produce signatures compatible with the Go client.
 */
public interface LimeWireNetworkSigner {
    /**
     * Produce a compact, Base64-encoded signature of the given UTF-8 message.
     */
    String signCompact(String msg);

    /**
     * Produces a 65-byte compact signature: [header (1)] [R (32)] [S (32)]
     */
    byte[] signCompact(byte[] input);

    static LimeWireNetworkSigner of(String input) {
        return LimeWireNetworkPrivateKeyFormat.detect(input)
                .getKeyConversionFunction()
                .andThen(DefaultLimeWireNetworkSigner::new)
                .apply(input);
    }

    static LimeWireNetworkSigner of(Path inputFile) {
        Objects.requireNonNull(inputFile, "inputFile must not be null");

        try {
            return LimeWireNetworkSigner.of(new String(Files.readAllBytes(inputFile), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
