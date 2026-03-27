package network.limewire.sdk;

import software.amazon.awssdk.http.SdkHttpRequest;

import java.util.*;

class S3ActionResolver {
    private static final Set<String> ALLOWED_ACTIONS = AllowedActionsLoader.load();

    interface S3Action {
        boolean shouldBufferResponse();

        boolean shouldBufferRequest();

        boolean isAllowed();
    }

    static S3Action resolve(SdkHttpRequest request) {
        String s3Action = resolveAction(request);

        return new S3Action() {
            @Override
            public boolean shouldBufferResponse() {
                return !s3Action.equals("s3:GetObject");
            }

            @Override
            public boolean shouldBufferRequest() {
                return s3Action.equals("s3:CompleteMultipartUpload") || s3Action.equals("s3:PutObjectTagging");
            }

            @Override
            public boolean isAllowed() {
                return ALLOWED_ACTIONS.contains(s3Action);
            }
        };
    }

    private static String resolveAction(SdkHttpRequest request) {
        if (request == null || request.method() == null || request.getUri() == null) {
            return null;
        }

        String rawPath = request.getUri().getRawPath();

        if (rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }

        String path = cleanPath(rawPath);
        if (".".equals(path) || path.isEmpty()) {
            path = "/";
        }

        // Count path segments excluding leading "/".
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        String[] segments = trimmed.split("/");
        int segCount = 0;
        for (String s : segments) {
            if (s != null && !s.isEmpty()) {
                segCount++;
            }
        }
        boolean isBucketPath = segCount == 1;
        boolean isObjectPath = segCount >= 2;

        // Helper: header lookup
        String copySource = request.firstMatchingHeader("x-amz-copy-source").orElse("");

        // ---- Order matters: most specific first ----

        Map<String, List<String>> queryParams = request.rawQueryParameters();
        String method = request.method().name();

        // Multipart uploads related
        if (isBucketPath && has(queryParams, "uploads") && "GET".equals(method)) {
            return "s3:ListMultipartUploads";
        }
        if (isObjectPath && has(queryParams, "uploadId") && "GET".equals(method)) {
            return "s3:ListParts";
        }
        if (isObjectPath && has(queryParams, "uploadId") && "DELETE".equals(method)) {
            return "s3:AbortMultipartUpload";
        }
        if (isObjectPath && has(queryParams, "uploadId") && "POST".equals(method)) {
            return "s3:CompleteMultipartUpload";
        }
        if (isObjectPath && has(queryParams, "partNumber") && has(queryParams, "uploadId") && "PUT".equals(method)) {
            if (!copySource.isEmpty()) return "s3:UploadPartCopy";
            return "s3:UploadPart";
        }

        // Tagging
        if (isBucketPath && has(queryParams, "tagging")) {
            switch (method) {
                case "GET":
                    return "s3:GetBucketTagging";
                case "DELETE":
                    return "s3:DeleteBucketTagging";
                case "PUT":
                    return "s3:PutBucketTagging";
            }
        }
        if (isObjectPath && has(queryParams, "tagging")) {
            switch (method) {
                case "GET":
                    return "s3:GetObjectTagging";
                case "DELETE":
                    return "s3:DeleteObjectTagging";
                case "PUT":
                    return "s3:PutObjectTagging";
            }
        }

        // Object lock / legal hold / retention
        if (isObjectPath && has(queryParams, "legal-hold")) {
            if ("GET".equals(method)) {
                return "s3:GetObjectLegalHold";
            }
            if ("PUT".equals(method)) {
                return "s3:PutObjectLegalHold";
            }
        }
        if (isObjectPath && has(queryParams, "retention")) {
            if ("GET".equals(method)) {
                return "s3:GetObjectRetention";
            }
            if ("PUT".equals(method)) {
                return "s3:PutObjectRetention";
            }
        }
        if (isBucketPath && has(queryParams, "object-lock")) {
            if ("GET".equals(method)) {
                return "s3:GetObjectLockConfiguration";
            }
            if ("PUT".equals(method)) {
                return "s3:PutObjectLockConfiguration";
            }
        }

        // Bucket operations
        if (isBucketPath && has(queryParams, "versioning") && "GET".equals(method)) {
            return "s3:GetBucketVersioning";
        }
        if (isBucketPath && has(queryParams, "location") && "GET".equals(method)) {
            return "s3:GetBucketLocation";
        }
        if (isBucketPath && has(queryParams, "session") && "GET".equals(method)) {
            return "s3:CreateSession";
        }
        if (isBucketPath && has(queryParams, "delete") && "POST".equals(method)) {
            return "s3:DeleteObjects";
        }
        if (isBucketPath && "HEAD".equals(method)) {
            return "s3:HeadBucket";
        }

        // ListObjects
        if (isBucketPath && "GET".equals(method)) {
            if ("2".equals(getFirst(queryParams, "list-type"))) return "s3:ListObjectsV2";
            if (has(queryParams, "delimiter") || has(queryParams, "encoding-type") || has(queryParams, "marker") || has(queryParams, "max-keys") || has(queryParams, "prefix")) {
                return "s3:ListObjects";
            }
        }

        // Object basic operations
        if (isObjectPath) {
            switch (method) {
                case "GET":
                    if (has(queryParams, "attributes")) return "s3:GetObjectAttributes";
                    return "s3:GetObject";
                case "PUT":
                    if (!copySource.isEmpty()) return "s3:CopyObject";
                    return "s3:PutObject";
                case "POST":
                    if (has(queryParams, "uploadId")) return "s3:CompleteMultipartUpload";
                    if (has(queryParams, "uploads")) return "s3:CreateMultipartUpload";
                    break;
                case "DELETE":
                    return "s3:DeleteObject";
                case "HEAD":
                    return "s3:HeadObject";
                default:
                    break;
            }
        }

        return "";
    }

    private static boolean has(Map<String, List<String>> q, String k) {
        return q != null && q.containsKey(k);
    }

    public static String getFirst(Map<String, List<String>> q, String k) {
        if (q == null) {
            return "";
        }

        List<String> vs = q.get(k);
        if (vs == null || vs.isEmpty() || vs.get(0) == null) {
            return "";
        }

        return vs.get(0);
    }

    /**
     * Minimal URL-path cleaner similar in spirit to Go's path.Clean:
     * - collapses multiple slashes
     * - resolves "." and ".."
     * - preserves leading "/"
     */
    private static String cleanPath(String rawPath) {
        boolean leadingSlash = rawPath.startsWith("/");
        String[] parts = rawPath.split("/+");

        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part == null || part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(part);
        }

        StringBuilder out = new StringBuilder();
        if (leadingSlash) out.append('/');
        Iterator<String> it = stack.iterator();
        while (it.hasNext()) {
            out.append(it.next());
            if (it.hasNext()) out.append('/');
        }

        return out.toString();
    }
}