package network.limewire.sdk;

import network.limewire.sdk.core.LimeWireNetworkSigner;
import network.limewire.sdk.core.DefaultLimeWireNetworkSigner;
import network.limewire.sdk.core.RequestIdGenerator;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class LimeWireNetworkPresignerTest {
    private static LimeWireNetworkPresigner presigner;

    @BeforeAll
    static void setUp() {
        String privateKeyHex = "a11429f9240b74b2020d4df1711261d0a7c8f4d9b9ef5ca72250659ac6da87fd";
        ECKey key = ECKey.fromPrivate(Hex.decode(privateKeyHex));
        LimeWireNetworkSigner signer = new DefaultLimeWireNetworkSigner(key);

        RequestIdGenerator requestIdGenerator = () -> "same-id-all-the-time";

        presigner = new LimeWireNetworkPresigner(signer, requestIdGenerator);
    }

    @Test
    public void shouldAddPresignedQueryParams() throws Exception {
        String baseUrl = "https://s3.amazonaws.com/bucket/file.txt?X-Amz-Expires=3600&X-Amz-Signature=ABC123%2F20251112%2Flmwrntwrk-region%2Fs3%2Faws4_request";

        URL newUrl = presigner.addParamsToPresignedURL(baseUrl, 5);
        //https://s3.amazonaws.com/bucket/file.txt?X-Amz-Expires=3600
        // &X-Amz-Signature=ABC123/20251112/lmwrntwrk-region/s3/aws4_request
        // &x-lmwrntwrk-request-id=same-id-all-the-time
        // &x-lmwrntwrk-signature=H+bg7WvxxroZzSiXLaJ8FKACNHRrKGpSYFJR+fRQi/uWPX/65HriCgTWtXQvtMdNuCsiANXOdIlBlBwKcobkFqQ=
        // &x-max-request-count=5

        assertThat(newUrl.toString())
                .contains("x-lmwrntwrk-request-id=same-id-all-the-time")
                .contains("x-lmwrntwrk-signature=");

        LimeWireNetworkPresigner.LimeWireNetworkPresignInfo info = LimeWireNetworkPresigner.extractPresignedParams(newUrl);
        assertThat(info.getMaxRequestCount()).isEqualTo(5);
        assertThat(info.getAwsSignature()).isEqualTo("ABC123/20251112/lmwrntwrk-region/s3/aws4_request");
        assertThat(info.getRequestId()).isNotNull();
        assertThat(info.getLimeWireNetworkSignature()).isNotNull();
    }

    @Test
    public void shouldThrowExceptionIfExpirationExceedsMax() {
        String baseUrl = "https://s3.amazonaws.com/test?X-Amz-Expires=20000&X-Amz-Signature=XYZ";

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> presigner.addParamsToPresignedURL(baseUrl, 1))
                .withMessageContaining("X-Amz-Expires exceeds");
    }

    @Test
    public void shouldRemoveParams() throws Exception {
        String url = "https://x.com/test?x-lmwrntwrk-request-id=1&x-lmwrntwrk-signature=2&x-max-request-count=3&X-Amz-Signature=Z";
        String cleaned = LimeWireNetworkPresigner.removeParams(url).toString();

        assertThat(cleaned)
                .doesNotContain("x-lmwrntwrk-request-id")
                .doesNotContain("x-lmwrntwrk-signature")
                .doesNotContain("x-max-request-count")
                .contains("X-Amz-Signature=Z");
    }
}

