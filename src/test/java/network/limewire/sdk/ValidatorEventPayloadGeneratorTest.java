package network.limewire.sdk;

import org.junit.jupiter.api.Test;

import java.util.Collections;

class ValidatorEventPayloadGeneratorTest {
    private final ValidatorEventPayloadGenerator generator = new ValidatorEventPayloadGenerator();

    @Test
    void legacyBehaviour() {
        this.generator.generate(null, null, Collections.emptyMap(), null, null);
    }

    @Test
    void shouldConvert() {
        this.generator.generate(null, null, Collections.emptyMap(), null, null);
    }
}