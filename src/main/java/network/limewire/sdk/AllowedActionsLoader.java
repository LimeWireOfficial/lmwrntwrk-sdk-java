package network.limewire.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AllowedActionsLoader {
    static Actions load() {
        try {
            ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(AllowedActionsLoader.class.getResourceAsStream("actions.json"), Actions.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static class Actions {
        public List<String> actions;
        public List<String> validatorActions;

        public Set<String> getAllowedActions() {
            return actions == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(actions));
        }

        public Set<String> getValidatorActions() {
            return validatorActions == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(validatorActions));
        }
    }
}
