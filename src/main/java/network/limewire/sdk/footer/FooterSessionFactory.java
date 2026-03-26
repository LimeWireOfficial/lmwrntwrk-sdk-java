package network.limewire.sdk.footer;

import network.limewire.sdk.core.LimeWireNetworkSigner;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class FooterSessionFactory {
    private final LimeWireNetworkSigner signer;
    private final BinaryFooterCodec binaryFooterCodec;
    private final FooterOptions footerOptions;

    public FooterSessionFactory(LimeWireNetworkSigner signer,
                                BinaryFooterCodec binaryFooterCodec,
                                FooterOptions footerOptions) {
        this.signer = signer;
        this.binaryFooterCodec = binaryFooterCodec;
        this.footerOptions = footerOptions;
    }

    private MessageDigest createHasher() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public FooterSession create() {
        return new FooterSession() {
            private long size = 0;
            private final List<HashTuple> hashes = new ArrayList<>();

            private byte[] signature;
            private byte[] footerBytes;

            private boolean finished = false;
            private final MessageDigest chunkHasher = createHasher();

            private final byte[] buffer = new byte[chunkSize()];
            private int bufferPos = 0;


            @Override
            public void onBytes(byte[] input) {
                assertNotFinished();
                if (input == null || input.length == 0) {
                    return;
                }

                int offset = 0;
                while (offset < input.length) {
                    int toCopy = Math.min(input.length - offset, chunkSize() - bufferPos);
                    System.arraycopy(input, offset, buffer, bufferPos, toCopy);

                    bufferPos += toCopy;
                    offset += toCopy;
                    this.size += toCopy;

                    if (bufferPos == chunkSize()) {
                        flushChunk(chunkSize());
                    }
                }
            }

            private void flushChunk(int chunkSize) {
                chunkHasher.reset();
                chunkHasher.update(buffer, 0, chunkSize);
                byte[] hash = chunkHasher.digest();
                this.hashes.add(new HashTuple(chunkSize, hash));
                bufferPos = 0;
            }

            @Override
            public void finish() {
                assertNotFinished();

                if (bufferPos > 0) {
                    flushChunk(bufferPos);
                }

                this.finished = true;

                byte[] hashOfHashes = computeHashOfHashes();
                byte[] sigInput = binaryFooterCodec.signingPayload(hashOfHashes, size);

                this.signature = signer.signCompact(sigInput);

                BinaryFooterCodec.BinaryFooter binaryFooter = new BinaryFooterCodec.BinaryFooter();
                binaryFooter.hashOfHashes = hashOfHashes;
                binaryFooter.size = size;
                binaryFooter.signature = signature;

                this.footerBytes = binaryFooterCodec.encode(binaryFooter);
            }

            private byte[] computeHashOfHashes() {
                MessageDigest hashesHasher = createHasher();
                for (HashTuple tuple : this.hashes) {
                    hashesHasher.update(tuple.getHash());
                }
                return hashesHasher.digest();
            }

            @Override
            public byte[] footerBytes() {
                assertFinished();
                return this.footerBytes;
            }

            @Override
            public ValidatorPayload validatorPayload() {
                if (this.size == 0) {
                    return null;
                }

                ValidatorPayload result = new ValidatorPayload();
                result.signature = this.signature;
                result.hashes = this.hashes;
                result.size = this.size;
                return result;
            }

            @Override
            public int chunkSize() {
                return footerOptions.getChunkSize();
            }

            @Override
            public boolean isFinished() {
                return this.finished;
            }

            private void assertNotFinished() {
                if (finished) {
                    throw new IllegalStateException("Footer already finished");
                }
            }

            private void assertFinished() {
                if (!finished) {
                    throw new IllegalStateException("Footer not finished yet");
                }
            }
        };
    }
}
