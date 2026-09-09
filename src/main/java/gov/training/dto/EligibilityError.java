package gov.training.dto;
import java.time.Instant;
import java.util.List;
public record EligibilityError(Instant timestamp, int status, String code, String message, List<String> reasons) {}

