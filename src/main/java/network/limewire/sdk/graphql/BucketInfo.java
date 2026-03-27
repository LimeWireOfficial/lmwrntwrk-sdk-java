package network.limewire.sdk.graphql;

public class BucketInfo {
    private long blockNumber;
    private String name;
    private String id;
    private String status;
    private PrimaryStorageProvider primaryStorageProvider;

    // Getters & setters
    public long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PrimaryStorageProvider getPrimaryStorageProvider() {
        return primaryStorageProvider;
    }

    public void setPrimaryStorageProvider(PrimaryStorageProvider p) {
        this.primaryStorageProvider = p;
    }

    public static class PrimaryStorageProvider {
        private String endpointUrl;

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }
    }

    @Override
    public String toString() {
        return "BucketInfo{" +
                "blockNumber=" + blockNumber +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", status='" + status + '\'' +
                ", endpointUrl='" + (primaryStorageProvider != null ? primaryStorageProvider.endpointUrl : null) + '\'' +
                '}';
    }
}

