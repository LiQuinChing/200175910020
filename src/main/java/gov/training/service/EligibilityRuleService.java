package gov.training.service;

import gov.training.dto.EligibilityRuleRequest;
import gov.training.exception.NominationOperationException;
import gov.training.model.EligibilityRule;
import gov.training.repository.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class EligibilityRuleService {
    private final EligibilityRuleRepository rules;
    private final TrainingRepository trainings;
    private final EligibilityRuleValidator validator;
    public EligibilityRuleService(EligibilityRuleRepository rules, TrainingRepository trainings, EligibilityRuleValidator validator) {
        this.rules = rules;
        this.trainings = trainings;
        this.validator = validator;
    }
    // Rule mutations share the nomination's parent-row lock, preventing mid-allocation changes.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<EligibilityRule> list(long trainingId) {
        lockTraining(trainingId);
        return rules.findByTraining(trainingId, false);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EligibilityRule create(long trainingId, EligibilityRuleRequest request) {
        lockTraining(trainingId);
        return rules.insert(trainingId, validator.validate(request));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EligibilityRule update(long ruleId, EligibilityRuleRequest request) {
        lockTraining(find(ruleId).trainingId());
        find(ruleId);
        return rules.update(ruleId, validator.validate(request));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void disable(long ruleId) {
        lockTraining(find(ruleId).trainingId());
        find(ruleId);
        rules.disable(ruleId);
    }
    private EligibilityRule find(long ruleId) {
        return rules.findById(ruleId).orElseThrow(() -> new NominationOperationException(
                HttpStatus.NOT_FOUND, "ELIGIBILITY_RULE_NOT_FOUND", "Eligibility rule was not found."));
    }
    private void lockTraining(long trainingId) {
        trainings.lockTrainingProgramme(trainingId).orElseThrow(() -> new NominationOperationException(
                HttpStatus.NOT_FOUND, "TRAINING_NOT_FOUND", "Training programme was not found."));
    }
}

