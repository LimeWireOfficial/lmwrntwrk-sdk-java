package network.limewire.sdk.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GraphQLClient {
    public static final String DEFAULT_ENDPOINT = "https://graph.limewire.network/subgraphs/name/lmwrntwrk-v1";

    private final String endpointUrl;
    private final String bearerToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GraphQLClient() {
        this(DEFAULT_ENDPOINT, null);
    }

    public GraphQLClient(String endpointUrl, String bearerToken) {
        this.endpointUrl = endpointUrl;
        this.bearerToken = bearerToken;
    }

    public <T> T query(String query, Map<String, Object> variables, Class<T> responseType) throws IOException {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        if (variables != null) {
            requestBody.put("variables", variables);
        }

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        byte[] rawRequestBody = jsonBody.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) new URL(this.endpointUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Content-Length", Integer.toString(rawRequestBody.length));

            if (this.bearerToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + this.bearerToken);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(rawRequestBody);
            }

            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("GraphQL request failed (" + statusCode + "): " + readAll(conn.getInputStream()));
            }

            JsonNode root = this.objectMapper.readTree(conn.getInputStream());

            if (root.has("errors")) {
                throw new IOException("GraphQL errors: " + root.get("errors").toString());
            }

            JsonNode dataNode = root.get("data");
            return objectMapper.treeToValue(dataNode, responseType);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Retrieves bucket info by bucket name.
     */
    public BucketInfo getBucketInfo(String bucketName) throws IOException {
        String query = "query MyQuery($name: String!) { " +
                "buckets(where: {name: $name}, first: 10) {" +
                " blockNumber name id status primaryStorageProvider { endpointUrl } } }";

        Map<String, Object> variables = Collections.singletonMap("name", bucketName);

        // Wrap data type to map GraphQL "data" structure
        BucketInfoResponse response = query(query, variables, BucketInfoResponse.class);
        if (response.getBuckets() == null || response.getBuckets().isEmpty()) {
            return null;
        }
        return response.getBuckets().get(0);
    }

    private static String readAll(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(1024);
            char[] buf = new char[4096];
            int r;
            while ((r = br.read(buf)) != -1) {
                sb.append(buf, 0, r);
            }
            return sb.toString();
        }
    }

}
