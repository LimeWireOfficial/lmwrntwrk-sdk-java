package network.limewire.sdk.credentials;

import network.limewire.sdk.LimeWireNetworkPrivateKeyFormat;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for creating {@link AwsCredentials} from all supported formats.
 */
public interface LimeWireNetworkCredentials extends AwsCredentials {
    static LimeWireNetworkCredentials from(String input) {
        return LimeWireNetworkPrivateKeyFormat.detect(input)
                .getKeyConversionFunction()
                .andThen(LimeWireNetworkEcKeyCredentials::new)
                .apply(input);
    }

    static LimeWireNetworkCredentials from(Path inputFile) {
        Objects.requireNonNull(inputFile, "inputFile must not be null");

        try {
            return LimeWireNetworkCredentials.from(new String(Files.readAllBytes(inputFile), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
