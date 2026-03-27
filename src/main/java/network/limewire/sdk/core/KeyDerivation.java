package network.limewire.sdk.core;

import network.limewire.sdk.LimeWireNetworkAsyncHttpClient;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.pqc.crypto.util.PrivateKeyFactory;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utilities to derive LimeWireNetwork access/secret keys in parity with the Go SDK.
 */
public final class KeyDerivation {
    private static final Logger logger = LoggerFactory.getLogger(LimeWireNetworkAsyncHttpClient.class);

    private KeyDerivation() {
    }

    public static ECKey loadFromHex(String hex) {
        return ECKey.fromPrivate(Hex.decode(hex.replace("0x", "")));
    }

    public static ECKey loadFromBase64Pem(String base64Pem) {
        if (base64Pem == null || base64Pem.isEmpty()) {
            throw new IllegalArgumentException("base64Pem is empty");
        }

        return loadFromPem(new String(Base64.getDecoder().decode(base64Pem), StandardCharsets.UTF_8));
    }

    public static ECKey loadFromPem(String pem) {
        try (Reader rdr = new StringReader(pem.trim());
             PemReader pemReader = new PemReader(rdr)) {

            PemObject pemObject = pemReader.readPemObject();
            if (pemObject == null) {
                throw new IllegalArgumentException("Invalid PEM input");
            }

            String type = pemObject.getType();
            byte[] content = pemObject.getContent();

            /*if (type.equals("EC PRIVATE KEY") || type.equals("PRIVATE KEY")) {
                // Private key (PKCS#8 or SEC1)
                ECPrivateKeyParameters privKey = (ECPrivateKeyParameters)
                        PrivateKeyFactory.createKey(content);
                return ECKey.fromPrivate(privKey.getD());
            } else if (type.equals("PUBLIC KEY")) {
                // Public key
                ECPublicKeyParameters pubKey = (ECPublicKeyParameters)
                        PublicKeyFactory.createKey(content);
                byte[] pubBytes = pubKey.getQ().getEncoded(true);
                return ECKey.fromPublicOnly(pubBytes);
            } else {
                throw new IllegalArgumentException("Unsupported PEM type: " + type);
            }*/

            if (type.equals("EC PRIVATE KEY")) {
                // Traditional SEC1 EC key (most common Bitcoin format)
                ASN1Sequence seq = ASN1Sequence.getInstance(content);
                ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(seq);
                BigInteger priv = ecPrivateKey.getKey();
                return ECKey.fromPrivate(priv, true);

            } else if (type.equals("PRIVATE KEY")) {
                // PKCS#8 encoded EC private key
                PrivateKeyInfo pkInfo = PrivateKeyInfo.getInstance(content);
                ECPrivateKeyParameters privKey = (ECPrivateKeyParameters)
                        PrivateKeyFactory.createKey(pkInfo);
                return ECKey.fromPrivate(privKey.getD(), true);

            } else if (type.equals("PUBLIC KEY")) {
                // Public key
                ECPublicKeyParameters pubKey = (ECPublicKeyParameters)
                        PublicKeyFactory.createKey(content);
                byte[] pubBytes = pubKey.getQ().getEncoded(true);
                return ECKey.fromPublicOnly(pubBytes);

            } else {
                throw new IllegalArgumentException("Unsupported PEM type: " + type);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static byte[] addressFromECKey(ECKey key) {
        // Get uncompressed public key bytes (65 bytes: 0x04 || X || Y)
        byte[] uncompressed = key.getPubKeyPoint().getEncoded(false);
        byte[] xy;
        if (uncompressed.length == 65 && uncompressed[0] == 0x04) {
            xy = new byte[64];
            System.arraycopy(uncompressed, 1, xy, 0, 64);
        } else if (uncompressed.length == 64) {
            xy = uncompressed;
        } else {
            throw new IllegalArgumentException("Unexpected pubkey length: " + uncompressed.length);
        }

        logger.debug("key pub: {}", Hex.toHexString(key.getPubKey()));

        // Keccak-256 hash of X||Y and take last 20 bytes
        Keccak.Digest256 keccak = new Keccak.Digest256();
        keccak.update(xy, 0, xy.length);
        byte[] hash = keccak.digest();
        byte[] addr = new byte[20];
        System.arraycopy(hash, hash.length - 20, addr, 0, 20);
        logger.debug("address: {}", Hex.toHexString(addr));
        return addr;
    }

    public static String generateAccessKeyFromAddress(byte[] addressBytes) {
        String b58 = base58Encode(addressBytes);
        return b58.length() > 20 ? b58.substring(0, 20) : b58;
    }

    public static String generateSecretKeyFromAddress(byte[] addressBytes) {
        byte[] sha1 = sha1Sum(addressBytes);
        return bytesToHex(sha1);
    }

    private static String base58Encode(byte[] input) {
        final String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        java.math.BigInteger x = new java.math.BigInteger(1, input);
        java.math.BigInteger base = java.math.BigInteger.valueOf(58);
        StringBuilder result = new StringBuilder();
        while (x.compareTo(java.math.BigInteger.ZERO) > 0) {
            java.math.BigInteger[] divRem = x.divideAndRemainder(base);
            x = divRem[0];
            int idx = divRem[1].intValue();
            result.insert(0, alphabet.charAt(idx));
        }
        return result.toString();
    }

    private static byte[] sha1Sum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

}