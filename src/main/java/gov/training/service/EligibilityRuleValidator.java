package gov.training.service;

import gov.training.dto.EligibilityRuleRequest;
import gov.training.exception.NominationOperationException;
import gov.training.model.EligibilityRuleType;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EligibilityRuleValidator {
    public EligibilityRuleRequest validate(EligibilityRuleRequest request) {
        if (request.ruleType() == null || request.operator() == null || request.ruleValue() == null || request.isActive() == null) {
            throw invalid("All eligibility rule fields are required.");
        }
        EligibilityRuleType type;
        try { type = EligibilityRuleType.valueOf(request.ruleType().trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw invalid("Invalid eligibility rule type."); }
        String operator = request.operator().trim().toUpperCase(Locale.ROOT);
        String value = request.ruleValue().trim();
        if (value.isEmpty() || value.length() > 255) throw invalid("Rule value must contain 1 to 255 characters.");
        switch (type) {
            case DEPARTMENT, DESIGNATION, GRADE -> {
                if (!operator.equals("=") && !operator.equals("IN")) throw invalid("This rule supports only = or IN.");
                if (operator.equals("IN")) {
                    for (String token : value.split(",", -1)) {
                        if (token.isBlank()) throw invalid("IN values must not contain empty entries.");
                    }
                }
            }
            case MIN_YEARS_OF_SERVICE -> {
                if (!operator.equals(">=")) throw invalid("Minimum service rules require the >= operator.");
                integer(value, true);
            }
            case NO_SAME_TRAINING_WITHIN_MONTHS -> {
                if (!operator.equals("=")) throw invalid("Training history rules require the = operator.");
                integer(value, false);
            }
        }
        return new EligibilityRuleRequest(type.name(), operator, value, request.isActive());
    }

    private void integer(String value, boolean allowZero) {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int parsed = Integer.parseInt(value);
            if (!allowZero && parsed == 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw invalid(allowZero ? "Years of service must be a nonnegative whole number."
                    : "History months must be a positive whole number.");
        }
    }
    private NominationOperationException invalid(String message) {
        return new NominationOperationException(HttpStatus.BAD_REQUEST, "INVALID_ELIGIBILITY_RULE", message);
    }
}

