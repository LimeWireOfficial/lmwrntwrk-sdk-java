package network.limewire.sdk;

import network.limewire.sdk.core.LimeWireNetworkSigner;
import network.limewire.sdk.core.RequestIdGenerator;
import network.limewire.sdk.footer.FooterOptions;
import software.amazon.awssdk.http.SdkHttpFullRequest;

/**
 * Enricher that takes an {@link SdkHttpFullRequest} and adapts it by including the required additional headers required
 * by LimeWireNetwork.
 */
class LimeWireNetworkRequestEnricher {
    private final RequestIdGenerator requestIdGen;
    private final LimeWireNetworkSigner signer;
    private final FooterOptions footerOptions;

    LimeWireNetworkRequestEnricher(RequestIdGenerator requestIdGen, LimeWireNetworkSigner signer, FooterOptions footerOptions) {
        this.requestIdGen = requestIdGen;
        this.signer = signer;
        this.footerOptions = footerOptions;
    }

    SdkHttpFullRequest enrichHeaders(SdkHttpFullRequest originalRequest, String authHeader) {
        String requestId = requestIdGen.generateId();
        String signature = signer.signCompact(requestId + authHeader);

        SdkHttpFullRequest.Builder result = originalRequest.toBuilder();

        String userAgent = originalRequest.firstMatchingHeader(LimeWireNetworkHeaders.USER_AGENT).orElse("");
        if (!userAgent.isEmpty()) {
            userAgent += " " + LimeWireNetworkVersion.getUserAgent();
        } else {
            userAgent = LimeWireNetworkVersion.getUserAgent();
        }

        SdkHttpFullRequest.Builder sdkRequestBuilder = result
                .putHeader(LimeWireNetworkHeaders.REQUEST_ID, requestId)
                .putHeader(LimeWireNetworkHeaders.SIGNATURE, signature)
                .putHeader(LimeWireNetworkHeaders.FOOTER_LENGTH, "109")
                .putHeader(LimeWireNetworkHeaders.CHUNK_SIZE, String.valueOf(footerOptions.getChunkSize()))
                .putHeader(LimeWireNetworkHeaders.USER_AGENT, userAgent);

        originalRequest
                .firstMatchingHeader("Content-Length")
                .filter(it -> !it.isEmpty())
                .ifPresent(contentLengthHeader -> {
                    sdkRequestBuilder.putHeader("Content-Length", String.valueOf(Long.parseLong(contentLengthHeader) + 109));
                });

        return result.build();
    }
}
