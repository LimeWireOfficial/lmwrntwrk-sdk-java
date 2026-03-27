package network.limewire.sdk;

import network.limewire.sdk.core.LimeWireNetworkSigner;
import network.limewire.sdk.core.RequestIdGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import software.amazon.awssdk.awscore.presigner.PresignedRequest;

import java.net.*;
import java.security.Security;
import java.util.LinkedHashMap;
import java.util.Map;

public class LimeWireNetworkPresigner {
    public static final String SIGNATURE_HEADER_NAME = "X-Amz-Signature";
    public static final long MAX_ALLOWED_PRESIGNED_REQUEST_DURATION_SECONDS = 4 * 60 * 60; // 4h

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final LimeWireNetworkSigner signer;
    private final RequestIdGenerator requestIdGenerator;

    public LimeWireNetworkPresigner(LimeWireNetworkSigner signer, RequestIdGenerator requestIdGenerator) {
        this.signer = signer;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * Adds LimeWireNetwork parameters to a presigned S3 URL.
     */
    public URL addParamsToPresignedURL(URL presignedURL, int maxRequestCount) {
        if (presignedURL == null) {
            throw new IllegalArgumentException("presignedURL is empty");
        }

        if (maxRequestCount <= 0) {
            throw new IllegalArgumentException("maxRequestCount must be > 0");
        }

        URI uri = toURI(presignedURL);

        Map<String, String> queryParams = parseQueryParams(uri.getRawQuery());

        // Enforce X-Amz-Expires <= 4h
        String expiresStr = queryParams.get("X-Amz-Expires");
        if (expiresStr != null) {
            int exp = Integer.parseInt(expiresStr);
            if (exp > MAX_ALLOWED_PRESIGNED_REQUEST_DURATION_SECONDS) {
                throw new IllegalArgumentException("X-Amz-Expires exceeds maximum allowed of 4h");
            }
        }

        String awsSignature = queryParams.get(SIGNATURE_HEADER_NAME);
        if (awsSignature == null) {
            throw new IllegalArgumentException("Missing " + SIGNATURE_HEADER_NAME + " in query");
        }

        String requestId = requestIdGenerator.generateId();
        String message = requestId + maxRequestCount + awsSignature;
        String signature = signer.signCompact(message);

        // Add new params
        queryParams.put(LimeWireNetworkHeaders.REQUEST_ID, requestId);
        queryParams.put(LimeWireNetworkHeaders.SIGNATURE, urlEncode(signature));
        queryParams.put(LimeWireNetworkHeaders.MAX_REQUEST_COUNT, String.valueOf(maxRequestCount));

        return rebuildUri(uri, queryParams);
    }

    private static URI toURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static URL toUrl(String url) {
        try {
            return URI.create(url).toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Adds LimeWireNetwork parameters to a presigned S3 URL.
     */
    public URL addParamsToPresignedURL(PresignedRequest presignedRequest, int maxRequestCount) {
        return addParamsToPresignedURL(presignedRequest.url(), maxRequestCount);
    }

    /**
     * Adds LimeWireNetwork parameters to a presigned S3 URL.
     */
    public URL addParamsToPresignedURL(String presignedURL, int maxRequestCount) {
        return addParamsToPresignedURL(toUrl(presignedURL), maxRequestCount);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Extracts LimeWireNetwork parameters from a presigned URL.
     */
    public static LimeWireNetworkPresignInfo extractPresignedParams(URL presignedURL) {
        if (presignedURL == null) {
            throw new IllegalArgumentException("presignedURL is empty");
        }

        URI uri = toURI(presignedURL);
        Map<String, String> q = parseQueryParams(uri.getRawQuery());

        LimeWireNetworkPresignInfo info = new LimeWireNetworkPresignInfo();
        info.requestId = require(q, LimeWireNetworkHeaders.REQUEST_ID);
        info.limeWireNetworkSignature = require(q, LimeWireNetworkHeaders.SIGNATURE);
        info.awsSignature = require(q, SIGNATURE_HEADER_NAME);

        String maxStr = require(q, LimeWireNetworkHeaders.MAX_REQUEST_COUNT);
        int max = Integer.parseInt(maxStr);
        if (max < 0) {
            throw new IllegalArgumentException(LimeWireNetworkHeaders.MAX_REQUEST_COUNT + " must be >= 0");
        }
        info.maxRequestCount = max;
        return info;
    }

    /**
     * Removes LimeWireNetwork query parameters from a URL string.
     */
    public static URL removeParams(String url) {
        URI uri = URI.create(url);
        Map<String, String> q = parseQueryParams(uri.getRawQuery());
        q.remove(LimeWireNetworkHeaders.REQUEST_ID);
        q.remove(LimeWireNetworkHeaders.SIGNATURE);
        q.remove(LimeWireNetworkHeaders.MAX_REQUEST_COUNT);
        return rebuildUri(uri, q);
    }

    private static String require(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("Missing query param: " + key);
        }

        return v;
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            String k = urlDecode(kv[0]);
            String v = kv.length > 1 ? urlDecode(kv[1]) : "";
            map.put(k, v);
        }
        return map;
    }

    private static URL rebuildUri(URI base, Map<String, String> queryParams) {
        try {
            StringBuilder queryBuilder = new StringBuilder();
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append("&");
                }

                queryBuilder.append(e.getKey())
                        .append("=")
                        .append(e.getValue());
            }

            return new URI(
                    base.getScheme(),
                    base.getAuthority(),
                    base.getPath(),
                    queryBuilder.toString(),
                    base.getFragment()
            ).toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static class LimeWireNetworkPresignInfo {
        private String requestId;
        private String limeWireNetworkSignature;
        private String awsSignature;
        private int maxRequestCount;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getLimeWireNetworkSignature() {
            return limeWireNetworkSignature;
        }

        public void setLimeWireNetworkSignature(String limeWireNetworkSignature) {
            this.limeWireNetworkSignature = limeWireNetworkSignature;
        }

        public String getAwsSignature() {
            return awsSignature;
        }

        public void setAwsSignature(String awsSignature) {
            this.awsSignature = awsSignature;
        }

        public int getMaxRequestCount() {
            return maxRequestCount;
        }

        public void setMaxRequestCount(int maxRequestCount) {
            this.maxRequestCount = maxRequestCount;
        }
    }

}
