package network.limewire.sdk.core;

import network.limewire.sdk.footer.FooterSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * FooterAppendingStream appends a LimeWireNetwork footer to the end of the underlying stream.
 */
public final class FooterAppendingInputStream extends InputStream {
    private final FooterSession footerSession;

    private final InputStream in;
    private final byte[] buffer;
    private boolean eof = false;
    private int footerPos = 0;

    public FooterAppendingInputStream(InputStream in, FooterSession footerSession) {
        this.in = in;
        this.footerSession = footerSession;
        this.buffer = new byte[footerSession.chunkSize()];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (footerSession.isFinished()) {
            byte[] footerBytes = footerSession.footerBytes();
            if (footerPos >= footerBytes.length) {
                return -1;
            }

            int n = Math.min(len, footerBytes.length - footerPos);
            System.arraycopy(footerBytes, footerPos, b, off, n);
            footerPos += n;
            return n;

        }

        // If EOF of main stream, generate footer
        if (eof) {
            try {
                footerSession.finish();
            } catch (Exception e) {
                throw new IOException("Footer creation failed", e);
            }
            return read(b, off, len);
        }

        int n = in.read(buffer, 0, Math.min(footerSession.chunkSize(), len));
        if (n > 0) {
            byte[] chunk = Arrays.copyOf(buffer, n);
            footerSession.onBytes(chunk);
            System.arraycopy(chunk, 0, b, off, n);
            return n;
        }

        if (n == -1) {
            eof = true;
            return read(b, off, len);
        }

        return n;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n == -1 ? -1 : one[0] & 0xFF;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
