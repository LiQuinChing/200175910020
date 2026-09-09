package gov.training.service;

import gov.training.dto.*;
import gov.training.exception.NominationOperationException;
import gov.training.model.*;
import gov.training.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EligibilityService {
    private final EligibilityRuleRepository rules;
    private final OfficerRepository officers;
    private final TrainingRepository trainings;
    private final NominationRepository nominations;
    private final EligibilityRuleValidator validator;
    private final Clock clock;
    public EligibilityService(EligibilityRuleRepository rules, OfficerRepository officers,
            TrainingRepository trainings, NominationRepository nominations, EligibilityRuleValidator validator, Clock clock) {
        this.rules = rules; this.officers = officers; this.trainings = trainings;
        this.nominations = nominations; this.validator = validator; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EligibilityResult checkEligibility(long trainingId, long officerId) {
        Officer officer = officers.findById(officerId).orElseThrow(() -> new NominationOperationException(
                HttpStatus.NOT_FOUND, "OFFICER_NOT_FOUND", "Officer was not found."));
        trainings.lockTrainingProgramme(trainingId).orElseThrow(() -> new NominationOperationException(
                HttpStatus.NOT_FOUND, "TRAINING_NOT_FOUND", "Training programme was not found."));
        var groups = new EnumMap<EligibilityRuleType, List<EligibilityRuleRequest>>(EligibilityRuleType.class);
        for (EligibilityRule rule : rules.findByTraining(trainingId, true)) {
            try {
                var validated = validator.validate(new EligibilityRuleRequest(
                        rule.ruleType(), rule.operator(), rule.ruleValue(), rule.isActive()));
                groups.computeIfAbsent(EligibilityRuleType.valueOf(validated.ruleType()), ignored -> new ArrayList<>()).add(validated);
            } catch (NominationOperationException ex) {
                // Invalid directly inserted rules must not silently grant admission.
                throw new NominationOperationException(HttpStatus.CONFLICT, "INVALID_ELIGIBILITY_CONFIGURATION",
                        "Eligibility rule " + rule.ruleId() + " is invalid: " + ex.getMessage());
            }
        }
        LocalDate today = LocalDate.now(clock);
        List<String> reasons = new ArrayList<>();
        for (var group : groups.entrySet()) {
            boolean matched = group.getValue().stream().anyMatch(rule -> matches(group.getKey(), rule, officer, trainingId, today));
            if (!matched) reasons.add(reason(group.getKey(), group.getValue()));
        }
        return new EligibilityResult(trainingId, officerId, reasons.isEmpty(), reasons);
    }

    private boolean matches(EligibilityRuleType type, EligibilityRuleRequest rule, Officer officer, long trainingId, LocalDate today) {
        return switch (type) {
            case DEPARTMENT -> tokens(rule).stream().anyMatch(value ->
                    same(value, officer.departmentName()) ||
                    (officer.departmentId() != null && value.equals(officer.departmentId().toString())));
            case DESIGNATION -> tokens(rule).stream().anyMatch(value -> same(value, officer.designation()));
            case GRADE -> tokens(rule).stream().anyMatch(value -> same(value, officer.grade()));
            case MIN_YEARS_OF_SERVICE -> officer.dateOfJoining() != null &&
                    !officer.dateOfJoining().isAfter(today) &&
                    Period.between(officer.dateOfJoining(), today).getYears() >= Integer.parseInt(rule.ruleValue());
            case NO_SAME_TRAINING_WITHIN_MONTHS -> !nominations.hasParticipationSince(
                    trainingId, officer.officerId(), today.minusMonths(Integer.parseInt(rule.ruleValue())), today);
        };
    }
    private List<String> tokens(EligibilityRuleRequest rule) {
        return rule.operator().equals("IN") ? Arrays.stream(rule.ruleValue().split(",")).map(String::trim).toList()
                : List.of(rule.ruleValue());
    }
    private boolean same(String expected, String actual) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }
    private String reason(EligibilityRuleType type, List<EligibilityRuleRequest> rules) {
        return switch (type) {
            case DEPARTMENT -> "Officer's department is not eligible for this training programme.";
            case DESIGNATION -> "Officer's designation does not match an allowed designation.";
            case GRADE -> "Officer's grade does not match an allowed grade.";
            case MIN_YEARS_OF_SERVICE -> "Minimum " + rules.stream().mapToInt(r -> Integer.parseInt(r.ruleValue())).min().orElseThrow()
                    + " years of service is required; a valid date of joining must be recorded.";
            case NO_SAME_TRAINING_WITHIN_MONTHS -> "Officer has previous participation in this training programme within the restricted period ("
                    + rules.stream().mapToInt(r -> Integer.parseInt(r.ruleValue())).min().orElseThrow() + " months).";
        };
    }
}

