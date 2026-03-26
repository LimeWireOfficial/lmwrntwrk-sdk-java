package network.limewire.sdk;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class StaticValidatorUrlSupplier implements ValidatorUrlSupplier {
    private final List<URL> urls;

    public StaticValidatorUrlSupplier(String url) {
        this.urls = Collections.singletonList(toEventUrl(url));
    }

    private URL toEventUrl(String baseUrl) {
        try {
            URI result = URI.create(baseUrl + "/events");
            return result.toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public StaticValidatorUrlSupplier(String first, String... rest) {
        List<String> result = new ArrayList<>();
        result.add(first);
        Collections.addAll(result, rest);

        this.urls = Collections.unmodifiableList(result.stream()
                .map(this::toEventUrl)
                .collect(Collectors.toList()));
    }

    public StaticValidatorUrlSupplier(List<URL> urls) {
        this.urls = Collections.unmodifiableList(urls);
    }

    @Override
    public CompletableFuture<URL> get() {
        return CompletableFuture.completedFuture(urls.get(ThreadLocalRandom.current().nextInt(urls.size())));
    }
}
