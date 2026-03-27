package network.limewire.sdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimeWireNetworkPemCredentialsTest {
    @Test
    void shouldConvert() {
        LimeWireNetworkPemCredentials limeWireNetworkPemCredentials = LimeWireNetworkPemCredentials
                .fromBase64Pem("LS0tLS1CRUdJTiBFQyBQUklWQVRFIEtFWS0tLS0tCk1IUUNBUUVFSUw1ZVB6Sy9XU08rcDBOa3I5MDFkUHd0dFAvYTRTQzlIcTJ6UDVPRjY0dWNvQWNHQlN1QkJBQUsKb1VRRFFnQUVDTGFQVFI4YUVuZ09WSitRMEQyeEJyNVNSYnJ2TUNjNnZSRUQ4QjFVSTNXb0dEeVRKcWNCNGFNQwp1MElGeGU4cFlWRWd4MitWbExpeDIwMmJDRGwyd2c9PQotLS0tLUVORCBFQyBQUklWQVRFIEtFWS0tLS0tCg==");
        assertThat(limeWireNetworkPemCredentials).isNotNull();
        assertThat(limeWireNetworkPemCredentials.accessKeyId()).isEqualTo("2KmbTA5NovyV84c2ju38");
        assertThat(limeWireNetworkPemCredentials.secretAccessKey()).isEqualTo("1d78e9bd4fe51edebff6f1e570e573f78ea98b0d");

    }
}