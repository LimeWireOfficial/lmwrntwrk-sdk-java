package network.limewire.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.limewire.sdk.footer.FooterSession;
import network.limewire.validator.events.*;
import software.amazon.awssdk.http.SdkHttpRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class ValidatorEventPayloadGenerator {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<String> generate(byte[] requestPayload,
                                     SdkHttpRequest httpRequest,
                                     Map<String, List<String>> responseHeaders,
                                     byte[] responsePayload,
                                     FooterSession.ValidatorPayload footer) {
        // This header is mandatory, if it's not present, we just return as no Event can be sent to a validator
        Optional<String> signatureHeader = getFirstMatchingHeader("x-lmwrntwrk-sp-signature", responseHeaders);
        if (!signatureHeader.isPresent()) {
            return Optional.empty();
        }

        StoreEventRequest storeEventRequest = new StoreEventRequest()
                .withStorageProviderS3Signature(signatureHeader.get())
                .withRequest(new Request()
                        .withBody(requestPayload != null ? new String(requestPayload) : null)
                        .withHeaders(toEventHeaders(httpRequest.headers()))
                        .withMethod(httpRequest.method().name())
                        .withUrl(stripHost(httpRequest))
                )
                .withResponse(new Response()
                        .withBody(responsePayload != null ? new String(responsePayload) : null)
                        .withHeaders(toEventHeaders(responseHeaders)));

        getFirstMatchingHeader("x-lmwrntwrk-sp-payload", responseHeaders)
                .ifPresent(storeEventRequest::withStorageProviderPayload);

        if (footer != null) {
            Optional<String> spFooterSignature = getFirstMatchingHeader("x-lmwrntwrk-sp-footer-signature", responseHeaders);
            if (!spFooterSignature.isPresent()) {
                return Optional.empty();
            }

            storeEventRequest.withFooter(new Footer()
                    .withClientSignature(toBase64(footer.getSignature()))
                    .withFileSize(footer.getSize())
                    .withStorageProviderSignature(spFooterSignature.get())
                    .withHashes(footer.getHashes()
                            .stream()
                            .map(hash -> Arrays.asList(toBase64(hash.getHash()), String.valueOf(hash.getLength())))
                            .collect(Collectors.toList()))
            );
        }

        try {
            return Optional.of(this.objectMapper.writeValueAsString(storeEventRequest));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private URI stripHost(SdkHttpRequest httpRequest) {
        try {
            URI uri = httpRequest.getUri();
            return new URI(null, null, uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String toBase64(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }

    private Optional<String> getFirstMatchingHeader(String headerName, Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(headerName))
                .findFirst()
                .map(Map.Entry::getValue)
                .filter(it -> !it.isEmpty())
                .map(it -> it.get(0));
    }

    private Headers toEventHeaders(Map<String, List<String>> source) {
        Headers result = new Headers();
        source.forEach((key, value) -> result.setAdditionalProperty(key, String.join(",", value)));
        return result;
    }
}
