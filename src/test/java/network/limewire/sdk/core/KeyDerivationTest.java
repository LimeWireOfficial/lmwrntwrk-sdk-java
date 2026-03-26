package network.limewire.sdk.core;

import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class KeyDerivationTest {

    // Provided Base64-encoded PEM from the issue
    private static final String PRIVATE_KEY_BASE64 =
            "LS0tLS1CRUdJTiBFQyBQUklWQVRFIEtFWS0tLS0tCk1IUUNBUUVFSUtFVUtma2tDM1N5QWcxTjhYRVNZZENueVBUWnVlOWNweUpRWlpyRzJvZjlvQWNHQlN1QkJBQUsKb1VRRFFnQUVSb3BjMWlKbk52VGEyT1NvT2FIYVZtTDF1V2hxTEc4Q0Q0QnQrc3lqOFgzMlJiYlc2aVd3cldRZwpzcEhieDY3MTBpRmhjdUhzNE4xWGozc2FxZmJ3ekE9PQotLS0tLUVORCBFQyBQUklWQVRFIEtFWS0tLS0tCg==";

    @Test
    void derivesExpectedAccessKeyFromProvidedPem() throws IOException {
        ECKey key = KeyDerivation.loadFromBase64Pem(PRIVATE_KEY_BASE64);
        assertNotNull(key);

        byte[] address = KeyDerivation.addressFromECKey(key);
        assertNotNull(address);
        assertEquals(20, address.length, "address must be 20 bytes");
        System.out.println("address: " + Hex.toHexString(address));

        String access = KeyDerivation.generateAccessKeyFromAddress(address);
        assertEquals("3UAdfsZgqZuJa8PZi9jS", access, "access key must match Go SDK output");

        String secret = KeyDerivation.generateSecretKeyFromAddress(address);
        assertNotNull(secret);
        System.out.println("secret: " + secret);
        assertEquals(40, secret.length(), "secret key is SHA-1 hex (40 chars)");
        assertTrue(secret.matches("[0-9a-f]{40}"), "secret must be lowercase hex");
        assertEquals("62b5dd60e3427e4def3ebfe51e0f7f142fefc683", secret, "secret key must match Go SDK output");
    }

    @Test
    void addressAndAccessChangeWithDifferentKey() {
        // Generate a random key to ensure logic works generally
        ECKey rnd = new ECKey();
        byte[] addr = KeyDerivation.addressFromECKey(rnd);
        assertEquals(20, addr.length);
        String access = KeyDerivation.generateAccessKeyFromAddress(addr);
        assertNotNull(access);
        assertTrue(access.length() > 0);
    }

    @Test
    void addressFromECDSAPub_KnownVector() {
        // Sample uncompressed public key coordinates for secp256k1 generator point
        String xHex = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798";
        String yHex = "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8";

        // Build ECPoint on secp256k1 from X, Y
        SecP256K1Curve curve = new SecP256K1Curve();
        BigInteger x = new BigInteger(xHex, 16);
        BigInteger y = new BigInteger(yHex, 16);
        ECPoint point = curve.createPoint(x, y);

        // Construct ECKey from public point
        ECKey pubKey = ECKey.fromPublicOnly(point.getEncoded(false)); // uncompressed 65 bytes

        // Compute address using implementation under test
        byte[] got = KeyDerivation.addressFromECKey(pubKey);

        // Build expected XY (left-padded 32 bytes each) and compute expected via the same shared logic
        byte[] xBytes = trimToUnsigned(x.toByteArray());
        byte[] yBytes = trimToUnsigned(y.toByteArray());
        byte[] xy = new byte[64];
        System.arraycopy(xBytes, 0, xy, 32 - xBytes.length, xBytes.length);
        System.arraycopy(yBytes, 0, xy, 64 - yBytes.length, yBytes.length);

        byte[] want = sharedAddressFromUncompressed(xy);
        // print want as hex
        System.out.println("want: " + Hex.toHexString(want));

        assertEquals(want.length, got.length, "unexpected address length");
        assertArrayEquals(want, got, "address mismatch");
    }

    // Helper to mirror shared.AddressFromUncompressed: keccak256(X||Y) then last 20 bytes
    private static byte[] sharedAddressFromUncompressed(byte[] xy) {
        org.bouncycastle.jcajce.provider.digest.Keccak.Digest256 keccak =
                new org.bouncycastle.jcajce.provider.digest.Keccak.Digest256();
        keccak.update(xy, 0, xy.length);
        byte[] hash = keccak.digest();
        byte[] addr = new byte[20];
        System.arraycopy(hash, hash.length - 20, addr, 0, 20);
        return addr;
    }

    // Remove a possible leading sign byte from BigInteger.toByteArray()
    private static byte[] trimToUnsigned(byte[] in) {
        if (in.length == 33 && in[0] == 0x00) {
            byte[] out = new byte[32];
            System.arraycopy(in, 1, out, 0, 32);
            return out;
        }
        if (in.length <= 32) return in;
        byte[] out = new byte[32];
        System.arraycopy(in, in.length - 32, out, 0, 32);
        return out;
    }
}