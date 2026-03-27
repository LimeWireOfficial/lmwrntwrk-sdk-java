package network.limewire.sdk;

import network.limewire.sdk.graphql.BucketInfo;
import network.limewire.sdk.graphql.GraphQLClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.endpoints.Endpoint;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointParams;
import software.amazon.awssdk.services.s3.endpoints.S3EndpointProvider;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Custom S3EndpointProvider that resolves endpoint URLs for bucket using data from the LimeWireNetwork Graph.
 */
public class LimeWireEndpointProvider implements S3EndpointProvider {
    private static final Logger logger = LoggerFactory.getLogger(LimeWireEndpointProvider.class);

    private final GraphQLClient graphQLClient;

    public LimeWireEndpointProvider(String graphQLEndpoint) {
        this.graphQLClient = new GraphQLClient(graphQLEndpoint, "");
    }

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(S3EndpointParams endpointParams) {
        String bucketName = endpointParams.bucket();
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name is required to resolve endpoint");
        }

        try {
            BucketInfo bucketInfo = graphQLClient.getBucketInfo(bucketName);
            if (bucketInfo == null || bucketInfo.getPrimaryStorageProvider() == null) {
                throw new IllegalStateException("No primary storage provider found for bucket: " + bucketName);
            }

            String endpointUrl = bucketInfo.getPrimaryStorageProvider().getEndpointUrl();
            if (endpointUrl == null || endpointUrl.isEmpty()) {
                throw new IllegalStateException("Resolved endpoint URL is empty for bucket: " + bucketName);
            }

            // TODO check if this is correct way
            if (endpointParams.forcePathStyle()) {
                endpointUrl = endpointUrl + "/" + bucketName;
            }

            logger.debug("Resolved endpoint URL: {}", endpointUrl);

            return CompletableFuture.completedFuture(Endpoint.builder()
                    .url(URI.create(endpointUrl))
                    .build()
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve S3 endpoint for bucket '" + bucketName + "'", e);
        }
    }
}