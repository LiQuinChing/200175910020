package gov.training.dto;
import jakarta.validation.constraints.*;
public record EligibilityRuleRequest(
        @NotBlank @Size(max = 50) String ruleType,
        @NotBlank @Size(max = 20) String operator,
        @NotBlank @Size(max = 255) String ruleValue,
        @NotNull Boolean isActive) {}

