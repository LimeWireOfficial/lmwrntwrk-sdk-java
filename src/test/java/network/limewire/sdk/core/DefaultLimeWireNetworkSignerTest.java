package network.limewire.sdk.core;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DefaultLimeWireNetworkSignerTest {

    private static String base64OfPemWithRawKey(byte[] raw32) {
        // Build a minimal PEM armor with raw 32-byte private key as body
        String innerBase64 = Base64.getEncoder().encodeToString(raw32);
        String pem = "-----BEGIN EC PRIVATE KEY-----\n" + innerBase64 + "\n-----END EC PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testFromBase64Pem_InvalidBase64() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> DefaultLimeWireNetworkSigner.fromBase64Pem("not-base64"));
    }

    @Test
    void testSha256() {
        String msg = "lmwrntwrk";

        byte[] hashed = sha256(msg.getBytes(StandardCharsets.UTF_8));
        String hashedHex = Hex.toHexString(hashed);

        assertThat(hashedHex).isEqualTo("a90cbb4d9f6010d7811b47e9671901560ccd64c7139e08287d5ed3cd094d3ffa");
    }

    @Test
    void testSignatureUsingFixedKey() {
        String privateKeyHex = "a11429f9240b74b2020d4df1711261d0a7c8f4d9b9ef5ca72250659ac6da87fd";
        ECKey key = ECKey.fromPrivate(Hex.decode(privateKeyHex));
        String msg = "lmwrntwrk";

        String res = new DefaultLimeWireNetworkSigner(key).signCompact(msg);
        assertThat(res).isEqualTo("H98j93R+X5B07+ZhCXlwgN/YMUx8xEgHzihfAc+TXWLdRgpXiDP/CrIwOQM9hk+X3MJ5iA6k1+xIH3kdNqFLQx4=");
    }

    @Test
    void testVerifySignatureWithRAndS() {
        ECKey key = new ECKey();
        DefaultLimeWireNetworkSigner signer = new DefaultLimeWireNetworkSigner(key);

        String msg = "test message";
        String sigB64 = signer.signCompact(msg);
        byte[] sig = Base64.getDecoder().decode(sigB64);

        byte[] hash = sha256(msg.getBytes(StandardCharsets.UTF_8));
        Sha256Hash h = Sha256Hash.wrap(hash);

        BigInteger r = new BigInteger(1, java.util.Arrays.copyOfRange(sig, 1, 33));
        BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(sig, 33, 65));
        ECKey.ECDSASignature ecdsaSig = new ECKey.ECDSASignature(r, s);

        assertThat(key.verify(h, ecdsaSig)).isTrue();
    }

    @Test
    void testSignCompact_ErrorOnNullKey() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new DefaultLimeWireNetworkSigner(null));
    }

    private static byte[] sha256(byte[] in) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            return d.digest(in);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
