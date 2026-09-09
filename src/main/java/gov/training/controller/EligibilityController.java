package gov.training.controller;

import gov.training.dto.*;
import gov.training.model.EligibilityRule;
import gov.training.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EligibilityController {
    private final EligibilityService eligibility;
    private final EligibilityRuleService rules;
    public EligibilityController(EligibilityService eligibility, EligibilityRuleService rules) {
        this.eligibility = eligibility; this.rules = rules;
    }
    @GetMapping("/trainings/{trainingId}/eligibility/{officerId}")
    public EligibilityResult check(@PathVariable @Positive long trainingId, @PathVariable @Positive long officerId) {
        return eligibility.checkEligibility(trainingId, officerId);
    }
    @GetMapping("/trainings/{trainingId}/eligibility-rules")
    public List<EligibilityRule> list(@PathVariable @Positive long trainingId) { return rules.list(trainingId); }
    @PostMapping("/trainings/{trainingId}/eligibility-rules")
    public ResponseEntity<EligibilityRule> create(@PathVariable @Positive long trainingId, @Valid @RequestBody EligibilityRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rules.create(trainingId, request));
    }
    @PutMapping("/eligibility-rules/{ruleId}")
    public EligibilityRule update(@PathVariable @Positive long ruleId, @Valid @RequestBody EligibilityRuleRequest request) {
        return rules.update(ruleId, request);
    }
    @DeleteMapping("/eligibility-rules/{ruleId}")
    public ResponseEntity<Void> disable(@PathVariable @Positive long ruleId) {
        rules.disable(ruleId);
        return ResponseEntity.noContent().build();
    }
}

