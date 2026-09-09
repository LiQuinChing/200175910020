package gov.training.model;
public record EligibilityRule(long ruleId, long trainingId, String ruleType,
        String operator, String ruleValue, boolean isActive) {}

