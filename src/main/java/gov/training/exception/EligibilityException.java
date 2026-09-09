package gov.training.exception;
import java.util.List;
public class EligibilityException extends RuntimeException {
    private final List<String> reasons;
    public EligibilityException(List<String> reasons) {
        super("Officer does not meet the eligibility requirements.");
        this.reasons = List.copyOf(reasons);
    }
    public List<String> getReasons() { return reasons; }
}

