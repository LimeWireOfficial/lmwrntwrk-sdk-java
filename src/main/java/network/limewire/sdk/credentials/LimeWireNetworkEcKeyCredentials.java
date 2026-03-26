package network.limewire.sdk.credentials;

import network.limewire.sdk.core.KeyDerivation;
import org.bitcoinj.crypto.ECKey;

import java.util.Objects;

class LimeWireNetworkEcKeyCredentials implements LimeWireNetworkCredentials {
    private final String accessKeyId;
    private final String secretAccessKey;

    LimeWireNetworkEcKeyCredentials(ECKey key) {
        Objects.requireNonNull(key, "key must not be null");

        byte[] addressBytes = KeyDerivation.addressFromECKey(key);
        this.accessKeyId = KeyDerivation.generateAccessKeyFromAddress(addressBytes);
        this.secretAccessKey = KeyDerivation.generateSecretKeyFromAddress(addressBytes);
    }

    @Override
    public String accessKeyId() {
        return this.accessKeyId;
    }

    @Override
    public String secretAccessKey() {
        return this.secretAccessKey;
    }
}
