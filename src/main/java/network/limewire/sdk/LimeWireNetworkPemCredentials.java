package network.limewire.sdk;

import network.limewire.sdk.core.KeyDerivation;
import org.bitcoinj.crypto.ECKey;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @deprecated use {@link network.limewire.sdk.credentials.LimeWireNetworkCredentials} instead
 */
@Deprecated
public class LimeWireNetworkPemCredentials implements AwsCredentials {
    private final String accessKeyId;
    private final String secretAccessKey;

    public LimeWireNetworkPemCredentials(String pem) {
        ECKey key = KeyDerivation.loadFromPem(pem);
        byte[] addressBytes = KeyDerivation.addressFromECKey(key);

        this.accessKeyId = KeyDerivation.generateAccessKeyFromAddress(addressBytes);
        this.secretAccessKey = KeyDerivation.generateSecretKeyFromAddress(addressBytes);
    }

    /**
     * @deprecated use {@link network.limewire.sdk.credentials.LimeWireNetworkCredentials#from(String)} instead
     */
    @Deprecated
    public static LimeWireNetworkPemCredentials fromBase64Pem(String base64Pem) {
        String pem = new String(Base64.getDecoder().decode(base64Pem), StandardCharsets.UTF_8);
        return new LimeWireNetworkPemCredentials(pem);
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
