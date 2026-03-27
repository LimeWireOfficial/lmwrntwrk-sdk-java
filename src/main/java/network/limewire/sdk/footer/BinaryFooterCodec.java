package network.limewire.sdk.footer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public interface BinaryFooterCodec {
    byte[] signingPayload(byte[] hashOfHashes, long size);

    byte[] encode(BinaryFooter footer);

    FullBinaryFooter decode(byte[] input);

    BinaryFooterCodec V1 = new BinaryFooterCodec() {
        private final byte[] MAGIC_BYTES = {(byte) 0xFA, (byte) 0xCE, (byte) 0xAF};
        private static final byte FOOTER_VERSION = 1;

        @Override
        public byte[] signingPayload(byte[] hashOfHashes, long size) {
            byte[] sizeBytes = ByteBuffer.allocate(8).putLong(size).array();
            return concat(hashOfHashes, sizeBytes);
        }

        private byte[] concat(byte[] first, byte[] second) {
            byte[] result = new byte[first.length + second.length];
            System.arraycopy(first, 0, result, 0, first.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }

        @Override
        public byte[] encode(BinaryFooter footer) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(MAGIC_BYTES);
                out.write(FOOTER_VERSION);
                out.write(footer.hashOfHashes);
                out.write(ByteBuffer.allocate(8).putLong(footer.size).array());
                out.write(footer.signature);
                return out.toByteArray();
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public FullBinaryFooter decode(byte[] input) {
            FullBinaryFooter result = new FullBinaryFooter();
            result.magicBytes = Arrays.copyOfRange(input, 0, 3);
            result.version = input[3];
            result.hashOfHashes = Arrays.copyOfRange(input, 4, 36);
            result.bigEndianSize = Arrays.copyOfRange(input, 36, 44);
            result.signature = Arrays.copyOfRange(input, 44, 109);
            return result;
        }
    };

    class BinaryFooter {
        byte[] hashOfHashes;
        long size;
        byte[] signature;
    }

    class FullBinaryFooter {
        byte[] magicBytes;
        byte version;
        byte[] hashOfHashes;
        byte[] bigEndianSize;
        byte[] signature;

        public byte[] getMagicBytes() {
            return magicBytes;
        }

        public byte getVersion() {
            return version;
        }

        public byte[] getHashOfHashes() {
            return hashOfHashes;
        }

        public byte[] getBigEndianSize() {
            return bigEndianSize;
        }

        public byte[] getSignature() {
            return signature;
        }
    }
}
