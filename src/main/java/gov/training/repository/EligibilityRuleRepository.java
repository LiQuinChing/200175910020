package gov.training.repository;

import gov.training.dto.EligibilityRuleRequest;
import gov.training.model.EligibilityRule;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EligibilityRuleRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<EligibilityRule> MAPPER = (rs, row) -> new EligibilityRule(
            rs.getLong("rule_id"), rs.getLong("training_id"), rs.getString("rule_type"),
            rs.getString("operator"), rs.getString("rule_value"), rs.getBoolean("is_active"));
    public EligibilityRuleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<EligibilityRule> findByTraining(long trainingId, boolean activeOnly) {
        return jdbc.query("SELECT * FROM training_eligibility_rule WHERE training_id = ?"
                + (activeOnly ? " AND is_active = TRUE" : "") + " ORDER BY rule_id", MAPPER, trainingId);
    }
    public Optional<EligibilityRule> findById(long ruleId) {
        return jdbc.query("SELECT * FROM training_eligibility_rule WHERE rule_id = ?", MAPPER, ruleId).stream().findFirst();
    }
    public EligibilityRule insert(long trainingId, EligibilityRuleRequest rule) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO training_eligibility_rule (training_id, rule_type, `operator`, rule_value, is_active)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[] {"rule_id"});
            statement.setLong(1, trainingId);
            statement.setString(2, rule.ruleType());
            statement.setString(3, rule.operator());
            statement.setString(4, rule.ruleValue());
            statement.setBoolean(5, rule.isActive());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return an eligibility rule ID.");
        return findById(key.longValue()).orElseThrow();
    }
    public EligibilityRule update(long ruleId, EligibilityRuleRequest rule) {
        jdbc.update("""
                UPDATE training_eligibility_rule SET rule_type = ?, `operator` = ?, rule_value = ?, is_active = ?
                WHERE rule_id = ?
                """, rule.ruleType(), rule.operator(), rule.ruleValue(), rule.isActive(), ruleId);
        return findById(ruleId).orElseThrow();
    }
    public void disable(long ruleId) {
        jdbc.update("UPDATE training_eligibility_rule SET is_active = FALSE WHERE rule_id = ?", ruleId);
    }
}

