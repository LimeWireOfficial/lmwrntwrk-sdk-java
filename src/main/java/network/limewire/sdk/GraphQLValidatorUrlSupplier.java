package network.limewire.sdk;

import network.limewire.sdk.graphql.GraphQLClient;
import network.limewire.sdk.graphql.ValidatorInfo;
import network.limewire.sdk.graphql.ValidatorResponse;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class GraphQLValidatorUrlSupplier implements ValidatorUrlSupplier {
    private final GraphQLClient graphQLClient;
    private final long cacheTtlMs;
    private List<URL> cachedUrls;
    private long lastFetchTime;

    private static final String GET_VALIDATORS_QUERY =
            "query GetValidators {" +
            "  validators(first: 3, where: {status: ENABLED}) {" +
            "    endpointUrl" +
            "  }" +
            "}";

    public GraphQLValidatorUrlSupplier(GraphQLClient graphQLClient) {
        this(graphQLClient, 3600000); // Default 1 hour cache
    }

    public GraphQLValidatorUrlSupplier(GraphQLClient graphQLClient, long cacheTtlMs) {
        this.graphQLClient = graphQLClient;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public CompletableFuture<URL> get() {
        return getValidators().thenApply(urls -> {
            if (urls == null || urls.isEmpty()) {
                throw new IllegalStateException("No enabled validators found");
            }
            return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
        });
    }

    private synchronized CompletableFuture<List<URL>> getValidators() {
        if (cachedUrls != null && (System.currentTimeMillis() - lastFetchTime < cacheTtlMs)) {
            return CompletableFuture.completedFuture(cachedUrls);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ValidatorResponse response = graphQLClient.query(GET_VALIDATORS_QUERY, null, ValidatorResponse.class);
                if (response == null || response.getValidators() == null) {
                    return null;
                }
                List<URL> urls = response.getValidators().stream()
                        .map(ValidatorInfo::getEndpointUrl)
                        .map(this::toEventUrl)
                        .collect(Collectors.toList());
                
                synchronized (this) {
                    this.cachedUrls = urls;
                    this.lastFetchTime = System.currentTimeMillis();
                }
                return urls;
            } catch (Exception e) {
                if (cachedUrls != null) {
                    // Fallback to stale cache if fetch fails
                    return cachedUrls;
                }
                throw new RuntimeException("Failed to fetch validators from GraphQL", e);
            }
        });
    }

    private URL toEventUrl(String baseUrl) {
        try {
            String url = baseUrl;
            if (!url.endsWith("/")) {
                url += "/";
            }
            URI result = URI.create(url + "events");
            return result.toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid validator URL: " + baseUrl, ex);
        }
    }
}
