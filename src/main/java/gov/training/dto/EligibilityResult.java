package gov.training.dto;
import java.util.List;
public record EligibilityResult(long trainingId, long officerId, boolean eligible, List<String> reasons) {
    public EligibilityResult { reasons = List.copyOf(reasons); }
}

