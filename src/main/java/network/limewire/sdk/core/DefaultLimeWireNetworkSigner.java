package network.limewire.sdk.core;

import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * {@code DefaultLimeWireNetwork} implements compact ECDSA signatures over secp256k1
 * compatible with Go btcec/ecdsa.SignCompact with compressed=true.
 */
public class DefaultLimeWireNetworkSigner implements LimeWireNetworkSigner {
    private final ECKey key;

    /**
     * @deprecated will be made package-private in the future
     */
    @Deprecated
    public DefaultLimeWireNetworkSigner(ECKey key) {
        Objects.requireNonNull(key, "key must not be null");
        this.key = key;
    }

    /**
     * Create a signer from a base64-encoded PEM blob. The PEM may contain either
     * raw 32-byte private key bytes or an RFC5915 EC PRIVATE KEY structure.
     *
     * @deprecated directly use {@link LimeWireNetworkSigner#of(String)} instead
     */
    public static DefaultLimeWireNetworkSigner fromBase64Pem(String base64Pem) {
        return (DefaultLimeWireNetworkSigner) LimeWireNetworkSigner.of(base64Pem);
    }

    @Override
    public String signCompact(String msg) {
        return Base64.getEncoder().encodeToString(signCompact(msg.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public byte[] signCompact(byte[] data) {
        byte[] hash = Sha256Hash.hash(data); // sha256.Sum256
        Sha256Hash h = Sha256Hash.wrap(hash);

        // Create canonical ECDSA signature (r,s), low-S normalized by bitcoinj
        ECKey.ECDSASignature sig = key.sign(h);

        // Compute recovery id (0..3)
        int recId = findRecoveryId(sig, h, key.getPubKeyPoint());
        if (recId == -1) {
            throw new IllegalStateException("Could not construct a recoverable signature");
        }

        // Build compact signature: header + 32-byte R + 32-byte S
        boolean compressed = true;
        int header = 27 + recId + (compressed ? 4 : 0);

        byte[] rBytes = bigIntegerTo32(sig.r);
        byte[] sBytes = bigIntegerTo32(sig.s);

        byte[] out = new byte[65];
        out[0] = (byte) header;
        System.arraycopy(rBytes, 0, out, 1, 32);
        System.arraycopy(sBytes, 0, out, 33, 32);
        return out;
    }

    private static byte[] bigIntegerTo32(BigInteger v) {
        byte[] bytes = v.toByteArray();
        if (bytes.length == 32) return bytes;
        if (bytes.length == 33 && bytes[0] == 0x00) {
            // Trim leading sign byte
            return Arrays.copyOfRange(bytes, 1, 33);
        }
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
            return padded;
        }
        throw new IllegalArgumentException("R/S too long");
    }

    // Brute-force recover to find recId that matches our public key
    private static int findRecoveryId(ECKey.ECDSASignature sig, Sha256Hash hash, ECPoint expectedPub) {
        for (int recId = 0; recId < 4; recId++) {
            ECKey recovered = recoverFromSignature(recId, sig, hash);
            if (recovered != null) {
                // Compare uncompressed point equality
                if (recovered.getPubKeyPoint().equals(expectedPub)) {
                    return recId;
                }
            }
        }
        return -1;
    }

    // Recover public key from (recId, signature, messageHash)
    private static ECKey recoverFromSignature(int recId, ECKey.ECDSASignature sig, Sha256Hash hash) {
        try {
            // bitcoinj’s ECKey has static recoverFromSignature in some versions;
            // if unavailable, use ECKeyLite which provides the same functionality.
            return ECKey.recoverFromSignature(recId, sig, hash, true);
//            ECPoint pub = ECKeyLite.recoverFromSignature(recId, sig, hash, true);
//            if (pub == null) return null;
//            return ECKey.fromPublicOnly(pub.getEncoded(true));
        } catch (Exception e) {
            return null;
        }
    }
}
