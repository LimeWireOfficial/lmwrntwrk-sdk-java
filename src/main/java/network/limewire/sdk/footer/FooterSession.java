package network.limewire.sdk.footer;

import java.util.List;

public interface FooterSession {
    void onBytes(byte[] input);

    void finish();

    byte[] footerBytes();

    ValidatorPayload validatorPayload();

    int chunkSize();

    boolean isFinished();

    class ValidatorPayload {
        byte[] signature;
        List<HashTuple> hashes;
        long size;

        public byte[] getSignature() {
            return signature;
        }

        public List<HashTuple> getHashes() {
            return hashes;
        }

        public long getSize() {
            return size;
        }
    }

    class HashTuple {
        private final long length;
        private final byte[] hash;

        public HashTuple(long length, byte[] hash) {
            this.length = length;
            this.hash = hash;
        }

        public long getLength() {
            return length;
        }

        public byte[] getHash() {
            return hash;
        }
    }
}

