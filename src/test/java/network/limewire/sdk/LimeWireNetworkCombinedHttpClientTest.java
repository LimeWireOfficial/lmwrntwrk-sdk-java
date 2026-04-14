package network.limewire.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import network.limewire.sdk.core.DefaultLimeWireNetworkSigner;
import network.limewire.sdk.core.DefaultRequestIdGenerator;
import network.limewire.sdk.core.KeyDerivation;
import network.limewire.sdk.footer.BinaryFooterCodec;
import network.limewire.sdk.footer.FooterOptions;
import network.limewire.validator.events.StoreEventRequest;
import org.bitcoinj.crypto.ECKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class LimeWireNetworkCombinedHttpClientTest {
    enum Client {
        SYNC, ASYNC
    }

    private static final WireMockServer wireMock = new WireMockServer(
            WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void setUp() {
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
        wireMock.stubFor(post("/validator-a/events").willReturn(ok()));

        wireMock.stubFor(get("/bucket/simple-get").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "IGSNDfoL8HxqQL+uaaadnd0x8paLIkUivp+ksvei+G2nFgknlUwy+lezOCwYjMmTETDw5vmc7k/qeCVDkZCgGUE=")
                .withHeader("x-lmwrntwrk-sp-footer-signature", "H8W7Aj1ORQG8MR2Ze/9QaTYPHH3rt/Fxvej/nvNXNbbdOrgSixh86chI+XuFh1EW6XHRVkUgp7hbhnR5UfN3Gpw=")
                .withHeader("x-lmwrntwrk-sp-payload", "{\"some-key\": \"some-value\"}")
                .withBody("Hello, LimeWireNetwork!")));

        wireMock.stubFor(post("/bucket/multipart-key?uploadId=uploadId").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "H/vgsIJFXxaHCoIWGXvr3VHuSfp+J+UNDnKvi4phopjRc7j+4C5eNWskx78zMN3NgMErvh/r19mzRy4hXjJtjSY=")
                .withHeader("x-lmwrntwrk-sp-footer-signature", "IHv3BRY9o/AxbPZ7jZ5+uhv2foIPH+O619Zmtfg5e8ReXaHwuXc/u1wa23uyy7t9Y+iW4dWwZuOuZfC3L2QZu9E=")
                .withHeader("x-lmwrntwrk-sp-payload", "{\"some-key\": \"some-value\"}")
                .withBody("<CompleteMultipartUploadResult>\n" +
                        "   <Location>https://sp1.strg.com/bucket/multipart-key</Location>\n" +
                        "   <Bucket>bucket</Bucket>\n" +
                        "   <Key>multipart-key</Key>\n" +
                        "   <ETag>eTag</ETag>\n" +
                        "   <ChecksumCRC32>checksumCRC32</ChecksumCRC32>\n" +
                        "   <ChecksumCRC32C>checksumCRC32C</ChecksumCRC32C>\n" +
                        "   <ChecksumCRC64NVME>checksumCRC64NVME</ChecksumCRC64NVME>\n" +
                        "   <ChecksumSHA1>checksumSHA1</ChecksumSHA1>\n" +
                        "   <ChecksumSHA256>checksumSHA256</ChecksumSHA256>\n" +
                        "   <ChecksumType>checksumType</ChecksumType>\n" +
                        "</CompleteMultipartUploadResult>")
        ));
    }

    @BeforeEach
    void resetWireMockRequests() {
        wireMock.resetRequests();
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void sendRequestAndResponseBodiesForCompleteMultiPart(Client client) {
        Consumer<CompleteMultipartUploadRequest.Builder> requestBuilder = request -> request
                .uploadId("uploadId")
                .bucket("bucket")
                .key("multipart-key")
                .multipartUpload(mpu -> mpu.parts(
                        CompletedPart.builder().partNumber(1).eTag("eTag").build(),
                        CompletedPart.builder().partNumber(2).eTag("eTag-2").build()
                ));

        S3Call<CompleteMultipartUploadResponse> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(createSyncClient().completeMultipartUpload(requestBuilder))
                : () -> createAsyncClient().completeMultipartUpload(requestBuilder);

        CompleteMultipartUploadResponse result = call.get();

        assertThat(result).isNotNull();
        assertThat(result.location()).isEqualTo("https://sp1.strg.com/bucket/multipart-key");
        assertThat(result.bucket()).isEqualTo("bucket");
        assertThat(result.key()).isEqualTo("multipart-key");

        wireMock.verify(postRequestedFor(urlEqualTo("/bucket/multipart-key?uploadId=uploadId"))
                .withHeader("x-lmwrntwrk-request-id", matching("[0-9A-Z]{26}"))
                .withHeader("x-lmwrntwrk-Signature", matching(".*"))
                .withHeader("x-lmwrntwrk-Footer-Length", equalTo("109"))
                .withHeader("Content-Length", equalTo("360"))
                .withHeader("User-Agent", containing("LmwrNtwrkJavaSdk/"))
                .withRequestBody(containing("<?xml version=\"1.0\" encoding=\"UTF-8\"?><CompleteMultipartUpload xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"><Part><ETag>eTag</ETag><PartNumber>1</PartNumber></Part><Part><ETag>eTag-2</ETag><PartNumber>2</PartNumber></Part></CompleteMultipartUpload>"))
        );

        AtomicReference<byte[]> footerSignature = new AtomicReference<>();
        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/bucket/multipart-key?uploadId=uploadId"))))
                .singleElement()
                .satisfies(request -> {
                    byte[] requestBody = request.getBody();
                    byte[] binaryFooter = Arrays.copyOfRange(requestBody, requestBody.length - 109, requestBody.length);

                    BinaryFooterCodec.FullBinaryFooter footer = BinaryFooterCodec.V1.decode(binaryFooter);

                    assertThat(footer.getMagicBytes()).containsExactly(0xFA, 0xCE, 0xAF);
                    assertThat(footer.getVersion()).isEqualTo((byte) 1);
                    assertThat(footer.getHashOfHashes()).asBase64Encoded().isEqualTo("SCCMxjkFo9MmOglBc9iY3vowKGnoi71CcHbEgS68VWA=");
                    assertThat(footer.getBigEndianSize()).asBase64Encoded().isEqualTo("AAAAAAAAAPs=");
                    assertThat(footer.getSignature()).hasSize(65);

                    footerSignature.set(footer.getSignature());
                });

        wireMock.verify(postRequestedFor(urlEqualTo("/validator-a/events")));

        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/validator-a/events"))))
                .singleElement()
                .satisfies(request -> {
                    byte[] requestBody = request.getBody();
                    StoreEventRequest storeEventRequest = new ObjectMapper().readValue(requestBody, StoreEventRequest.class);

                    assertThat(storeEventRequest).isNotNull();
                    assertThat(storeEventRequest.getRequest()).isNotNull();
                    assertThat(storeEventRequest.getRequest().getBody()).isNotNull();

                    assertThat(storeEventRequest.getResponse().getBody()).isNotNull();

                    assertThat(footerSignature.get())
                            .isNotNull()
                            .asBase64Encoded()
                            .isEqualTo(storeEventRequest.getFooter().getClientSignature());
                });
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void doNotSendResponseBodyForBinaryResponse(Client client) {
        Consumer<GetObjectRequest.Builder> requestBuilder = request -> request.bucket("bucket").key("simple-get");

        S3Call<ResponseBytes<GetObjectResponse>> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(createSyncClient().getObject(requestBuilder, ResponseTransformer.toBytes()))
                : () -> createAsyncClient().getObject(requestBuilder, AsyncResponseTransformer.toBytes());

        ResponseBytes<GetObjectResponse> response = call.get();

        assertThat(response.asUtf8String()).isEqualTo("Hello, LimeWireNetwork!");

        wireMock.verify(getRequestedFor(urlEqualTo("/bucket/simple-get"))
                .withHeader("x-lmwrntwrk-request-id", matching("[0-9A-Z]{26}"))
                .withHeader("x-lmwrntwrk-Signature", matching(".*"))
                .withHeader("x-lmwrntwrk-Footer-Length", equalTo("109"))
                .withoutHeader("Original-Content-Length")
                .withRequestBody(absent())
        );

        wireMock.verify(postRequestedFor(urlEqualTo("/validator-a/events")));

        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/validator-a/events"))))
                .singleElement()
                .satisfies(request -> {
                    byte[] requestBody = request.getBody();
                    StoreEventRequest storeEventRequest = new ObjectMapper().readValue(requestBody, StoreEventRequest.class);

                    assertThat(storeEventRequest).isNotNull();
                    assertThat(storeEventRequest.getResponse()).isNotNull();
                    assertThat(storeEventRequest.getResponse().getBody()).isNull();

                    assertThat(storeEventRequest.getFooter()).isNull();
                });
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void doNotSendRequestBodyForBinaryRequest(Client client) {
        wireMock.stubFor(put("/bucket/simple-put").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "IBotkxuDGJ5neUy3f0+fhMW6/A1suQ5Bdprl92WUpfiRVkQL8UTcZs0S1+c9B7tAtoOLWwZ6l+zM+88ux6wRDrk=")
                .withHeader("x-lmwrntwrk-sp-footer-signature", "II+F1tKkDKdXRxh8KHaW1IZQqc1mT+8ufQ5olz4sFdirQOs5p5BbroLop7o7oKDT09uHqVqEKEtalZOhpedolBM=")
                .withHeader("x-lmwrntwrk-sp-payload", "{\"some-key\":\"some-value\"}")
                .withHeader("x-amz-request-id", "1889FF43CCC1380C")));

        Consumer<PutObjectRequest.Builder> requestBuilder = request -> request.bucket("bucket").key("simple-put");
        S3Call<PutObjectResponse> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(this.createSyncClient().putObject(requestBuilder, RequestBody.fromString("Hello, LimeWireNetwork!")))
                : () -> this.createAsyncClient().putObject(requestBuilder, AsyncRequestBody.fromString("Hello, LimeWireNetwork!"));

        PutObjectResponse response = call.get();
        assertThat(response).isNotNull();

        wireMock.verify(putRequestedFor(urlEqualTo("/bucket/simple-put"))
                .withHeader("x-lmwrntwrk-request-id", matching("[0-9A-Z]{26}"))
                .withHeader("x-lmwrntwrk-Signature", matching(".*"))
                .withHeader("x-lmwrntwrk-Footer-Length", equalTo("109"))
                .withHeader("Content-Length", equalTo("132"))
        );

        wireMock.verify(postRequestedFor(urlEqualTo("/validator-a/events")));

        AtomicReference<byte[]> footerSignature = new AtomicReference<>();
        assertThat(WireMock.findAll(putRequestedFor(urlEqualTo("/bucket/simple-put"))))
                .singleElement()
                .satisfies(request -> {
                    byte[] requestBody = request.getBody();
                    byte[] messageBytes = "Hello, LimeWireNetwork!".getBytes(StandardCharsets.UTF_8);
                    assertThat(requestBody).startsWith(messageBytes);

                    byte[] binaryFooter = Arrays.copyOfRange(requestBody, requestBody.length - 109, requestBody.length);
                    BinaryFooterCodec.FullBinaryFooter footer = BinaryFooterCodec.V1.decode(binaryFooter);

                    assertThat(footer.getMagicBytes()).containsExactly(0xFA, 0xCE, 0xAF);
                    assertThat(footer.getVersion()).isEqualTo((byte) 1);
                    assertThat(footer.getHashOfHashes()).asBase64Encoded().isEqualTo("Vff+DSBy/r2rEW54/DUtHZvKexNqBRApElPo7fdM8L8=");
                    assertThat(footer.getBigEndianSize()).asBase64Encoded().isEqualTo("AAAAAAAAABc=");
                    assertThat(footer.getSignature()).hasSize(65);

                    footerSignature.set(footer.getSignature());
                });

        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/validator-a/events"))))
                .singleElement()
                .satisfies(request -> {
                    StoreEventRequest storeEventRequest = new ObjectMapper().readValue(request.getBody(), StoreEventRequest.class);

                    assertThat(storeEventRequest).isNotNull();
                    assertThat(storeEventRequest.getStorageProviderS3Signature()).isEqualTo("IBotkxuDGJ5neUy3f0+fhMW6/A1suQ5Bdprl92WUpfiRVkQL8UTcZs0S1+c9B7tAtoOLWwZ6l+zM+88ux6wRDrk=");
                    assertThat(storeEventRequest.getStorageProviderPayload()).isEqualTo("{\"some-key\":\"some-value\"}");

                    assertThat(storeEventRequest.getRequest().getBody()).isNull();
                    assertThat(storeEventRequest.getRequest())
                            .isNotNull()
                            .satisfies(requestBody -> {
                                assertThat(requestBody).isNotNull();
                                assertThat(requestBody.getBody()).isNull();
                                assertThat(requestBody.getMethod()).isEqualTo("PUT");
                                assertThat(requestBody.getUrl())
                                        .hasNoHost()
                                        .hasPath("/bucket/simple-put");
                            });

                    assertThat(storeEventRequest.getRequest().getHeaders()).isNotNull();
                    assertThat(storeEventRequest.getRequest().getHeaders().getAdditionalProperties())
                            .contains(entry("x-lmwrntwrk-footer-length", "109"))
                            .containsKeys("x-lmwrntwrk-request-id", "x-lmwrntwrk-signature");

                    assertThat(storeEventRequest.getResponse()).isNotNull();
                    assertThat(storeEventRequest.getResponse().getBody()).isNotNull();
                    assertThat(storeEventRequest.getResponse().getHeaders()).isNotNull();
                    assertThat(storeEventRequest.getResponse().getHeaders().getAdditionalProperties())
                            .contains(
                                    entry("x-lmwrntwrk-sp-signature", "IBotkxuDGJ5neUy3f0+fhMW6/A1suQ5Bdprl92WUpfiRVkQL8UTcZs0S1+c9B7tAtoOLWwZ6l+zM+88ux6wRDrk="),
                                    entry("x-lmwrntwrk-sp-footer-signature", "II+F1tKkDKdXRxh8KHaW1IZQqc1mT+8ufQ5olz4sFdirQOs5p5BbroLop7o7oKDT09uHqVqEKEtalZOhpedolBM="),
                                    entry("x-lmwrntwrk-sp-payload", "{\"some-key\":\"some-value\"}"),
                                    entry("x-amz-request-id", "1889FF43CCC1380C"));

                    assertThat(storeEventRequest.getFooter())
                            .isNotNull()
                            .satisfies(footer -> {
                                assertThat(footer.getClientSignature()).isNotNull();
                                assertThat(footer.getFileSize()).isEqualTo(23);
                                assertThat(footer.getStorageProviderSignature()).isEqualTo("II+F1tKkDKdXRxh8KHaW1IZQqc1mT+8ufQ5olz4sFdirQOs5p5BbroLop7o7oKDT09uHqVqEKEtalZOhpedolBM=");
                                assertThat(footer.getHashes()).containsExactly(Arrays.asList("I4bUjHzMBcq4u4ieOvVvhmrhuCylKynWrg/Yj/oZeOo=", "23"));
                                assertThat(footer.getClientSignature()).isNotNull();

                            });

                    assertThat(footerSignature.get())
                            .isNotNull()
                            .asBase64Encoded()
                            .isEqualTo(storeEventRequest.getFooter().getClientSignature());
                });
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void handleSingleChunkProperly(Client client) {
        wireMock.stubFor(put("/bucket/put-from-file").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "IBotkxuDGJ5neUy3f0+fhMW6/A1suQ5Bdprl92WUpfiRVkQL8UTcZs0S1+c9B7tAtoOLWwZ6l+zM+88ux6wRDrk=")
                .withHeader("x-lmwrntwrk-sp-footer-signature", "II+F1tKkDKdXRxh8KHaW1IZQqc1mT+8ufQ5olz4sFdirQOs5p5BbroLop7o7oKDT09uHqVqEKEtalZOhpedolBM=")
                .withHeader("x-lmwrntwrk-sp-payload", "{\"some-key\":\"some-value\"}")
                .withHeader("x-amz-request-id", "1889FF43CCC1380C")));

        Consumer<PutObjectRequest.Builder> requestBuilder = request -> request.bucket("bucket").key("put-from-file");
        File inputFile = new File("src/test/resources/test-data/test-image.png");

        S3Call<PutObjectResponse> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(this.createSyncClient().putObject(requestBuilder, RequestBody.fromFile(inputFile)))
                : () -> this.createAsyncClient().putObject(requestBuilder, AsyncRequestBody.fromFile(inputFile));

        PutObjectResponse response = call.get();
        assertThat(response).isNotNull();

        wireMock.verify(putRequestedFor(urlEqualTo("/bucket/put-from-file"))
                .withHeader("x-lmwrntwrk-request-id", matching("[0-9A-Z]{26}"))
                .withHeader("x-lmwrntwrk-Signature", matching(".*"))
                .withHeader("x-lmwrntwrk-Footer-Length", equalTo("109"))
                .withHeader("Content-Length", equalTo("20957"))
        );

        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/validator-a/events"))))
                .singleElement()
                .satisfies(request -> {
                    StoreEventRequest storeEventRequest = new ObjectMapper().readValue(request.getBody(), StoreEventRequest.class);
                    // chunkSize is set to 10MB by default, so a single 2MB file should have 1 chunk
                    assertThat(storeEventRequest.getFooter().getHashes())
                            .hasSize(1)
                            .containsExactly(Arrays.asList("dHdWQwgcNsxT+G5Eik2ZhCPrn9LURww2ZdwKdZEckCQ=", "20848"));
                });
    }

    private InputStream deterministicDataWithSize(int size) {
        byte[] out = new byte[size];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (i & 0xFF);
        }
        return new ByteArrayInputStream(out);
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void handleMultipleChunksProperly(Client client) {
        wireMock.stubFor(put("/bucket/big-file").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "IBotkxuDGJ5neUy3f0+fhMW6/A1suQ5Bdprl92WUpfiRVkQL8UTcZs0S1+c9B7tAtoOLWwZ6l+zM+88ux6wRDrk=")
                .withHeader("x-lmwrntwrk-sp-footer-signature", "II+F1tKkDKdXRxh8KHaW1IZQqc1mT+8ufQ5olz4sFdirQOs5p5BbroLop7o7oKDT09uHqVqEKEtalZOhpedolBM=")
                .withHeader("x-lmwrntwrk-sp-payload", "{\"some-key\":\"some-value\"}")
                .withHeader("x-amz-request-id", "1889FF43CCC1380C")));

        Consumer<PutObjectRequest.Builder> requestBuilder = request -> request.bucket("bucket").key("big-file");

        S3Call<PutObjectResponse> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(this.createSyncClient().putObject(requestBuilder, RequestBody.fromInputStream(deterministicDataWithSize(15_000_000), 15_000_000)))
                : () -> this.createAsyncClient().putObject(requestBuilder, AsyncRequestBody.fromInputStream(deterministicDataWithSize(15_000_000), 15_000_000L, Executors.newSingleThreadExecutor()));

        PutObjectResponse response = call.get();
        assertThat(response).isNotNull();

        wireMock.verify(putRequestedFor(urlEqualTo("/bucket/big-file"))
                .withHeader("x-lmwrntwrk-request-id", matching("[0-9A-Z]{26}"))
                .withHeader("x-lmwrntwrk-Signature", matching(".*"))
                .withHeader("x-lmwrntwrk-Footer-Length", equalTo("109"))
                .withHeader("Content-Length", equalTo("15000109"))
        );

        assertThat(WireMock.findAll(postRequestedFor(urlEqualTo("/validator-a/events"))))
                .singleElement()
                .satisfies(request -> {
                    StoreEventRequest storeEventRequest = new ObjectMapper().readValue(request.getBody(), StoreEventRequest.class);
                    // chunkSize is set to 10MB by default, so a single 2MB file should have 1 chunk
                    assertThat(storeEventRequest.getFooter().getHashes())
                            .hasSize(2)
                            .containsExactly(
                                    Arrays.asList("rs88Krisp0hSvKB7VBNs7LP9r9w1VABo7ZUsC4lTjg0=", "10485760"),
                                    Arrays.asList("eeVKHrzY4pAnc0d8GnH45aYPXJ5hmt7Hm1eVpIh7p4s=", "4514240")
                                    );
                });
    }

    @ParameterizedTest
    @EnumSource(Client.class)
    void doNotSendNonWhitelistedEventsToValidator(Client client) {
        wireMock.stubFor(get("/bucket?list-type=2").willReturn(ok()
                .withHeader("x-lmwrntwrk-sp-signature", "some-signature")
                .withBody("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                        "   <Name>bucket</Name>" +
                        "   <Prefix></Prefix>" +
                        "   <KeyCount>0</KeyCount>" +
                        "   <MaxKeys>1000</MaxKeys>" +
                        "   <IsTruncated>false</IsTruncated>" +
                        "</ListBucketResult>")));

        Consumer<ListObjectsV2Request.Builder> requestBuilder = request -> request.bucket("bucket");

        S3Call<ListObjectsV2Response> call = client == Client.SYNC
                ? () -> CompletableFuture.completedFuture(createSyncClient().listObjectsV2(requestBuilder))
                : () -> createAsyncClient().listObjectsV2(requestBuilder);

        ListObjectsV2Response result = call.get();
        assertThat(result).isNotNull();

        wireMock.verify(getRequestedFor(urlEqualTo("/bucket?list-type=2")));

        // Wait a bit to ensure async publisher has no reason to call validator
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }

        wireMock.verify(0, postRequestedFor(urlEqualTo("/validator-a/events")));
    }

    @FunctionalInterface
    interface S3Call<T> {
        CompletableFuture<T> execute();

        default T get() {
            try {
                return execute().get();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private S3Client createSyncClient() {
        ECKey key = new ECKey();
        byte[] addressBytes = KeyDerivation.addressFromECKey(key);
        String accessKey = KeyDerivation.generateAccessKeyFromAddress(addressBytes);
        String secretKey = KeyDerivation.generateSecretKeyFromAddress(addressBytes);

        LimeWireNetworkHttpClient httpClient = new LimeWireNetworkHttpClient(
                ApacheHttpClient.create(),
                FooterOptions.defaultOptions(),
                new DefaultLimeWireNetworkSigner(key),
                new DefaultRequestIdGenerator(),
                new StaticValidatorUrlSupplier("http://localhost:" + wireMock.port() + "/validator-a"),
                new DefaultValidatorEventPublisher()
        );

        return S3Client.builder()
                .region(Region.of("lmwrntwrk-region"))
                .endpointOverride(URI.create("http://localhost:" + wireMock.port()))
                .httpClient(httpClient)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(cfg -> cfg.pathStyleAccessEnabled(true).chunkedEncodingEnabled(false))
                .build();
    }

    private S3AsyncClient createAsyncClient() {
        ECKey key = new ECKey();
        byte[] addressBytes = KeyDerivation.addressFromECKey(key);
        String accessKey = KeyDerivation.generateAccessKeyFromAddress(addressBytes);
        String secretKey = KeyDerivation.generateSecretKeyFromAddress(addressBytes);

        LimeWireNetworkAsyncHttpClient httpClient = new LimeWireNetworkAsyncHttpClient(
                NettyNioAsyncHttpClient.builder().build(),
                FooterOptions.defaultOptions(),
                new DefaultLimeWireNetworkSigner(key),
                new DefaultRequestIdGenerator(),
                new StaticValidatorUrlSupplier("http://localhost:" + wireMock.port() + "/validator-a"),
                new DefaultValidatorEventPublisher()
        );

        return S3AsyncClient.builder()
                .region(Region.of("lmwrntwrk-region"))
                .endpointOverride(URI.create("http://localhost:" + wireMock.port()))
                .httpClient(httpClient)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(cfg -> cfg.pathStyleAccessEnabled(true))
                .build();
    }
}
