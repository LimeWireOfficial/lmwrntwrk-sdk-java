package network.limewire.sdk.credentials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import static org.assertj.core.api.Assertions.assertThat;

class LimeWireNetworkCredentialsTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "be5e3f32bf5923bea74364afdd3574fc2db4ffdae120bd1eadb33f9385eb8b9c",
            "0xbe5e3f32bf5923bea74364afdd3574fc2db4ffdae120bd1eadb33f9385eb8b9c",
            "LS0tLS1CRUdJTiBFQyBQUklWQVRFIEtFWS0tLS0tCk1IUUNBUUVFSUw1ZVB6Sy9XU08rcDBOa3I5MDFkUHd0dFAvYTRTQzlIcTJ6UDVPRjY0dWNvQWNHQlN1QkJBQUsKb1VRRFFnQUVDTGFQVFI4YUVuZ09WSitRMEQyeEJyNVNSYnJ2TUNjNnZSRUQ4QjFVSTNXb0dEeVRKcWNCNGFNQwp1MElGeGU4cFlWRWd4MitWbExpeDIwMmJDRGwyd2c9PQotLS0tLUVORCBFQyBQUklWQVRFIEtFWS0tLS0tCg==",
            "-----BEGIN EC PRIVATE KEY-----\n" +
                    "MHQCAQEEIL5ePzK/WSO+p0Nkr901dPwttP/a4SC9Hq2zP5OF64ucoAcGBSuBBAAK\n" +
                    "oUQDQgAECLaPTR8aEngOVJ+Q0D2xBr5SRbrvMCc6vRED8B1UI3WoGDyTJqcB4aMC\n" +
                    "u0IFxe8pYVEgx2+VlLix202bCDl2wg==\n" +
                    "-----END EC PRIVATE KEY-----"
    })
    void shouldGenerateCredentials(String input) {
        AwsCredentials credentials = LimeWireNetworkCredentials.from(input);
        assertThat(credentials).isNotNull();
        assertThat(credentials.accessKeyId()).isEqualTo("2KmbTA5NovyV84c2ju38");
        assertThat(credentials.secretAccessKey()).isEqualTo("1d78e9bd4fe51edebff6f1e570e573f78ea98b0d");
    }

    @Test
    void shouldGenerateCredentialsFromMinimalKey() {
        String key = "-----BEGIN EC PRIVATE KEY-----\n" +
                "MC4CAQEEIJnMS61WFIrXYv1ARb7P5eE8krodTzj2LNRR2TEBf7yPoAcGBSuBBAAK\n" +
                "-----END EC PRIVATE KEY-----";

        AwsCredentials credentials = LimeWireNetworkCredentials.from(key);
        assertThat(credentials).isNotNull();
        assertThat(credentials.accessKeyId()).isEqualTo("2FCv9FeNnthw2A1d6JtZ");
        assertThat(credentials.secretAccessKey()).isEqualTo("315c53e15a01d52964612a4fe39111b8a82e6694");
    }
}