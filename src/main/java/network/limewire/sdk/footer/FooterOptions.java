package network.limewire.sdk.footer;

public class FooterOptions {
    private int chunkSize;

    public static FooterOptions defaultOptions() {
        FooterOptions footerOptions = new FooterOptions();
        footerOptions.setChunkSize(10_485_760);
        return footerOptions;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public FooterOptions setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        return this;
    }
}
