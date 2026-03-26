package network.limewire.sdk;

import network.limewire.sdk.core.KeyDerivation;
import org.bitcoinj.crypto.ECKey;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

public enum LimeWireNetworkPrivateKeyFormat {
    RAW_HEX(KeyDerivation::loadFromHex),
    PEM(KeyDerivation::loadFromPem),
    PEM_BASE64(KeyDerivation::loadFromBase64Pem);

    private final Function<String, ECKey> keyConversionFunction;

    LimeWireNetworkPrivateKeyFormat(Function<String, ECKey> keyConversionFunction) {
        this.keyConversionFunction = keyConversionFunction;
    }

    public Function<String, ECKey> getKeyConversionFunction() {
        return keyConversionFunction;
    }

    public static LimeWireNetworkPrivateKeyFormat detect(String input) {
        Objects.requireNonNull(input, "input must not be null");

        if (input.startsWith("-----BEGIN")) {
            return PEM;
        }

        if (input.matches("^(0x)?[0-9a-fA-F]{64}$")) {
            return RAW_HEX;
        }

        if (input.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            return PEM_BASE64;
        }

        throw new IllegalArgumentException("Unrecognized private-key format; allowed: " + Arrays.toString(values()));
    }
}
