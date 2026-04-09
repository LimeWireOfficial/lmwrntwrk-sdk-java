package network.limewire.sdk.graphql;

import java.util.List;

public class ValidatorResponse {
    private List<ValidatorInfo> validators;

    public List<ValidatorInfo> getValidators() {
        return validators;
    }

    public void setValidators(List<ValidatorInfo> validators) {
        this.validators = validators;
    }
}
