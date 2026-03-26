package network.limewire.sdk.core;

import network.limewire.sdk.footer.BinaryFooterCodec;
import network.limewire.sdk.footer.FooterOptions;
import network.limewire.sdk.footer.FooterSession;
import network.limewire.sdk.footer.FooterSessionFactory;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class FooterAppendingInputStreamTest {
    private final LimeWireNetworkSigner signer = new DefaultLimeWireNetworkSigner(ECKey.fromPrivate(Hex.decode("8870cfc22e8d150220a76192bba8b9ec76e71c7af11bf6dc83fe12c3cd384211")));
    private final FooterSessionFactory factory = new FooterSessionFactory(signer, BinaryFooterCodec.V1, new FooterOptions().setChunkSize(10244));

    @Test
    void testReadSingleChunkStream() throws Exception {
        byte[] data = "HelloLimewireNetwork".getBytes();
        ByteArrayInputStream baseStream = new ByteArrayInputStream(data);
        FooterSession footerSession = factory.create();

        FooterAppendingInputStream stream = new FooterAppendingInputStream(baseStream, footerSession);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8];
        int n;
        while ((n = stream.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        byte[] result = out.toByteArray();

        // Must start with the original data
        assertArrayEquals(Arrays.copyOfRange(result, 0, data.length), data, "Output should start with the original input data");

        // Verify footer magic sequence is present
        int footerStart = findMagic(result);
        assertTrue(footerStart > 0, "Footer magic bytes should appear in the stream");

        // Footer should contain version byte after magic
        assertEquals(1, result[footerStart + 3], "Footer version byte should be 1");

        // Verify footer length > 32 + 8 (hashes + total + signature)
        int footerLength = result.length - footerStart;
        assertTrue(footerLength > 32 + 8, "Footer should contain hash-of-hashes + total + signature");

        stream.close();
    }

    @Test
    void testReadMultipleChunks() throws Exception {
        byte[] data = new byte[5000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        FooterSession footerSession = factory.create();
        ByteArrayInputStream baseStream = new ByteArrayInputStream(data);
        FooterAppendingInputStream stream = new FooterAppendingInputStream(baseStream, footerSession);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = stream.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        byte[] result = out.toByteArray();

        // Verify all original data is intact before footer
        assertTrue(startsWith(result, data), "Result should begin with full original data");

        // Find magic bytes indicating footer start
        int footerPos = findMagic(result);
        assertTrue(footerPos > 0, "Footer magic bytes should exist");

        // Extract total length from footer and verify
        ByteBuffer bb = ByteBuffer.wrap(result, footerPos + 4 + 32, 8);
        long totalInFooter = bb.getLong();
        assertEquals(data.length, totalInFooter, "Footer should record total byte count");
    }

    @Test
    void testEmptyStreamStillProducesFooter() throws Exception {
        ByteArrayInputStream baseStream = new ByteArrayInputStream(new byte[0]);
        FooterSession footerSession = factory.create();

        FooterAppendingInputStream stream = new FooterAppendingInputStream(baseStream, footerSession);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = stream.read()) != -1) {
            out.write(b);
        }

        byte[] result = out.toByteArray();
        assertTrue(result.length > 0, "Even empty input should produce a footer");
        assertEquals((byte) 0xFA, result[0], "Footer should start with magic 0xFA");
    }

    // Helper methods
    private static boolean startsWith(byte[] full, byte[] prefix) {
        if (full.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (full[i] != prefix[i]) return false;
        }
        return true;
    }

    private static int findMagic(byte[] data) {
        byte[] magic = {(byte) 0xFA, (byte) 0xCE, (byte) 0xAF};
        for (int i = 0; i < data.length - magic.length; i++) {
            if (data[i] == magic[0] && data[i + 1] == magic[1] && data[i + 2] == magic[2]) {
                return i;
            }
        }
        return -1;
    }
}