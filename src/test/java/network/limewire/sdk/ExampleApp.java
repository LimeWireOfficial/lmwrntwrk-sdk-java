package network.limewire.sdk;

import network.limewire.sdk.core.DefaultLimeWireNetworkSigner;
import network.limewire.sdk.core.DefaultRequestIdGenerator;
import network.limewire.sdk.core.KeyDerivation;
import network.limewire.sdk.footer.FooterOptions;
import org.bitcoinj.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class ExampleApp {
    private static final Logger logger = LoggerFactory.getLogger(ExampleApp.class);

    public static void main(String[] args) throws IOException {
        logger.info("LimeWireNetwork SDK Java demo");

        // 1) Read configuration from env
        String bucket = getenvRequired("DEMO_LMWRNTWRK_DESTINATION_BUCKET");
        String endpointUri = System.getenv().getOrDefault("LMWRNTWRK_ENDPOINT_URI", "https://sp1.strg.com");
        String pkBase64 = System.getenv("DEMO_LMWRNTWRK_PRIVATE_KEY_BASE64"); // base64 of private key PEM file for the api key created on the LimeWire Network

        // 2) Load/generate ECKey for LimeWireNetwork signing
        ECKey key;
        if (pkBase64 != null && !pkBase64.isEmpty()) {
            logger.info("Private key value {}", pkBase64);
            key = KeyDerivation.loadFromPem(pkBase64);
            logger.info("Using EC private key from env (pubkey: {})", Hex.toHexString(key.getPubKey()));
        } else {
            key = new ECKey();
            logger.info("No DEMO_LMWRNTWRK_PRIVATE_KEY_KEY_BASE64 provided — using random key (pubkey: {})", Hex.toHexString(key.getPubKey()));
        }

        // 2.1) Derive S3-compatible access/secret keys
        byte[] addressBytes = KeyDerivation.addressFromECKey(key);
        String accessKey = KeyDerivation.generateAccessKeyFromAddress(addressBytes);
        String secretKey = KeyDerivation.generateSecretKeyFromAddress(addressBytes);
        logger.info("Derived accessKey={}, secretKey={}", accessKey, secretKey);

        // 3) Build the LimeWireNetwork wrapped HTTP client (adds headers and appends footer)
        DefaultLimeWireNetworkSigner bnSigner = new DefaultLimeWireNetworkSigner(key);
        DefaultRequestIdGenerator reqIdGen = new DefaultRequestIdGenerator();

        SdkHttpClient wrappedClient = new LimeWireNetworkHttpClient(
                ApacheHttpClient.builder().build(),
                FooterOptions.defaultOptions(),
                bnSigner,
                reqIdGen,
                new DefaultValidatorEventPublisher());
        SdkAsyncHttpClient wrappedAsyncClient = new LimeWireNetworkAsyncHttpClient(
                NettyNioAsyncHttpClient.builder().build(),
                FooterOptions.defaultOptions(),
                bnSigner,
                reqIdGen,
                new DefaultValidatorEventPublisher());

        // 4) Build an S3 client with:
        //    - fixed endpoint URI (no dynamic resolution)
        //    - path-style access
        //    - static credentials derived from the public key
        //    - LimeWireNetworkHttpClient attached
        S3Configuration s3cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        S3Client s3 = S3Client.builder()
                .region(Region.of("lmwrntwrk-region"))
//                .endpointOverride(URI.create(endpointUri)) // for testing
                .endpointProvider(new LimeWireEndpointProvider()) // override graph endpoint if needed eg new LimeWireEndpointProvider("http://graph-node-admin.localhost:8000/subgraphs/name/bn-test-1")
                .overrideConfiguration(c -> c.retryStrategy(cfg -> cfg.maxAttempts(1)))
                .httpClient(wrappedClient)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(s3cfg)
                .build();

        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(Region.of("lmwrntwrk-region"))
//                .endpointOverride(URI.create(endpointUri)) // for testing
                .endpointProvider(new LimeWireEndpointProvider()) // override graph endpoint if needed eg new LimeWireEndpointProvider("http://graph-node-admin.localhost:8000/subgraphs/name/bn-test-1")
                .overrideConfiguration(c -> c.retryStrategy(cfg -> cfg.maxAttempts(1)))
                .httpClient(wrappedAsyncClient)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(s3cfg)
                .build();

        //////////////////////////////
        /// SYNC CLIENT TESTS
        /// /////////////////////////

        // 5) S3 operations similar to the Go demo
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
        String prefix = "sdk-java-demo-" + formatter.format(Instant.now()) + "/";

        // 5.1 Create bucket (expected to fail; buckets are created on-chain)
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            logger.info("Bucket " + bucket + " created but should not be allowed");
        } catch (S3Exception e) {
            logger.info("Bucket " + bucket + " creating failed with expected error: " + e.awsErrorDetails().errorMessage());
        }

        // 5.2 Put a simple text object
        String textObjectKey = prefix + "test-msg1.txt";
        logger.info("Uploading text file under prefix: {}", prefix);
        PutObjectResponse putObjectResponse = s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(textObjectKey).build(),
                RequestBody.fromString("Hello, LimeWireNetwork!", StandardCharsets.UTF_8)
        );
        logger.info("Put object: {}, etag: {}", textObjectKey, putObjectResponse.eTag());

        // 5.3 Upload an image file
        String imageFileName = "test-image.png";
        String imageObjectKey = prefix + imageFileName;
        logger.info("Uploading image file '{}'...", imageObjectKey);

        Path imageFilePath = Paths.get("src/test/resources/test-data", imageFileName);
        if (Files.exists(imageFilePath)) {
            PutObjectResponse putImageRes = s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(imageObjectKey).build(),
                    RequestBody.fromFile(imageFilePath)
            );
            logger.info("Image file uploaded successfully. ETag: {}", putImageRes.eTag());
        } else {
            logger.warn("Image file not found at {}, skipping upload.", imageFilePath.toAbsolutePath());
        }

        // 5.4 List objects
        logger.info("Listing objects in bucket ... ");
        ListObjectsV2Response list = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build());
        logger.info("Objects in bucket:");
        list.contents().forEach(o -> logger.info("key={} size={}", o.key(), o.size()));

        // 5.5 Get the text object back and print its body
        logger.info("Downloading '{}' ...", textObjectKey);
        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(textObjectKey).build());
        logger.info("Get object etag: {}", bytes.response().eTag());
        logger.info("Downloaded content: {}", bytes.asUtf8String());


        //////////////////////////////
        /// PRESIGNED URLS TESTS
        /// /////////////////////////
        logger.info("Creating presigner ...");
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of("lmwrntwrk-region"))
                .endpointOverride(URI.create(endpointUri)) // for testing
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .s3Client(s3)
                .serviceConfiguration(s3cfg)
                .build();
        // Create S3 presigned GET URL
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(textObjectKey)
                .build();

        logger.info("Presigning object ...");
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getObjectRequest)
                .build();

        String presignedUrl = presigner.presignGetObject(presignRequest).url().toString();
        logger.info("Presigned AWS S3 URL: {}", presignedUrl);

        // Add LimeWireNetwork query params
        LimeWireNetworkPresigner limeWireNetworkPresigner = new LimeWireNetworkPresigner(bnSigner, reqIdGen);
        URL psWithBn = limeWireNetworkPresigner.addParamsToPresignedURL(presignedUrl, 1);
        logger.info("Presigned LimeWireNetwork URL: {}", psWithBn);

        try {
            // Perform first HTTP GET
            int firstStatus = doGet(psWithBn);
            if (firstStatus != 200) {
                throw new RuntimeException("Expected HTTP 200, got " + firstStatus);
            }
            logger.info("First request succeeded with 200 OK");

            // Perform a few more HTTP GETs — expect 429 (Too Many Requests) eventually
            boolean got429 = false;
            for (int i = 0; i < 5; i++) {
                int status = doGet(psWithBn);
                if (status == 429) {
                    got429 = true;
                    logger.info("Request {} failed as expected with 429 Too Many Requests", i + 2);
                    break;
                } else if (status == 200) {
                    logger.info("Request {} also succeeded with 200 OK", i + 2);
                } else {
                    throw new RuntimeException("Expected 200 or 429, got " + status);
                }
            }

            if (!got429) {
                throw new RuntimeException("Expected 429 Too Many Requests after several attempts, but it never happened.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        //////////////////////////////
        /// ASYNC CLIENT TESTS
        /// /////////////////////////
        try {
            // s3 async put
            PutObjectResponse putAsyncResponse = s3AsyncClient.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(textObjectKey).build(),
                    AsyncRequestBody.fromString("Hello, LimeWireNetwork!", StandardCharsets.UTF_8)
            ).get();
            logger.info("PutAsyncResponse etag: {}", putAsyncResponse.eTag());

            // get
            ResponseBytes<GetObjectResponse> getObjectResponse = s3AsyncClient.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(textObjectKey).build(),
                    AsyncResponseTransformer.toBytes()
            ).get();
            logger.info("GetAsync object etag: {}", getObjectResponse.response().eTag());
            logger.info("GetAsync Body: {}", getObjectResponse.asUtf8String());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        //////////////////////////////
        /// END
        /// /////////////////////////
        presigner.close();
        wrappedClient.close();
        s3.close();
        wrappedAsyncClient.close();
        s3AsyncClient.close();
        logger.info("Demo completed.");
    }

    private static String getenvRequired(String name) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return v;
    }

    private static int doGet(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        int status = conn.getResponseCode();

        if (status == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                logger.info("Response body: {}", sb);
            }
        } else {
            logger.info("GET {} returned {}", url, status);
        }

        conn.disconnect();
        return status;
    }

}
