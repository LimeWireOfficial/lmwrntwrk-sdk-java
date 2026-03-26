package network.limewire.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AllowedActionsLoader {
    static Set<String> load() {
        try {
            ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            Nested nested = objectMapper.readValue(AllowedActionsLoader.class.getResourceAsStream("actions.json"), Nested.class);
            return Collections.unmodifiableSet(new HashSet<>(nested.actions));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static class Nested {
        public List<String> actions;
    }
}
