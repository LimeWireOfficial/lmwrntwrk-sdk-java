package network.limewire.sdk.graphql;

import java.util.List;

public class BucketInfoResponse {
    private List<BucketInfo> buckets;

    public List<BucketInfo> getBuckets() {
        return buckets;
    }

    public void setBuckets(List<BucketInfo> buckets) {
        this.buckets = buckets;
    }
}

