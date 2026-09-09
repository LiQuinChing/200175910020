package gov.training;

import gov.training.dto.*;
import gov.training.exception.*;
import gov.training.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:eligibility;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
@Import(TrainingEligibilityTest.FixedTime.class)
class TrainingEligibilityTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() { return Clock.fixed(Instant.parse("2026-09-09T10:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EligibilityService eligibility;
    @Autowired EligibilityRuleService rules;
    @Autowired NominationService nominations;
    @Autowired MockMvc mvc;

    @BeforeEach void seed() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for (String statement : Files.readString(Path.of("schema.sql")).split(";")) {
            if (statement.stripLeading().startsWith("CREATE TABLE")) jdbc.execute(statement.replace("ENGINE=InnoDB", ""));
        }
        jdbc.update("INSERT INTO department (department_id, department_name, code) VALUES (10, 'Finance', 'FIN'), (20, 'Administration', 'ADM')");
        jdbc.update("INSERT INTO training_programme (training_id, training_name, max_participants) VALUES (101, 'Public Administration', 1), (102, 'Procurement', 40)");
        jdbc.update("""
                INSERT INTO officer (officer_id, officer_name, department_id, designation, grade, date_of_joining)
                VALUES (1001, 'Officer A', 10, 'Manager', 'Grade I', '2021-09-09'),
                       (1002, 'Officer B', 20, 'Analyst', 'Grade II', '2023-09-09'),
                       (1003, 'Officer C', NULL, NULL, NULL, NULL)
                """);
    }
    private long rule(String type, String operator, String value) {
        return rules.create(101, new EligibilityRuleRequest(type, operator, value, true)).ruleId();
    }
    private void nominate(long officer, long department) {
        nominations.create(new CreateNominationRequest(101L, officer, department));
    }

    @Test void noRulesAndInactiveRulesAllowAdmission() {
        assertThat(eligibility.checkEligibility(101, 1003).eligible()).isTrue();
        long id = rule("GRADE", "=", "Grade III");
        rules.disable(id);
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        assertThat(rules.list(101)).hasSize(1).allMatch(r -> !r.isActive());
        assertThat(nominations.create(new CreateNominationRequest(101L, 1001L, 10L)).status().name()).isEqualTo("CONFIRMED");
        assertThat(nominations.create(new CreateNominationRequest(101L, 1002L, 20L)).status().name()).isEqualTo("WAITING_LIST");
    }

    @Test void categoriesUseAndAndValuesUseOr() {
        rule("DEPARTMENT", "IN", "Budget");
        rule("DEPARTMENT", "IN", "finance");
        rule("DESIGNATION", "=", "Manager");
        rule("DESIGNATION", "IN", "Director, Supervisor");
        rule("GRADE", "=", "Grade I");
        rule("MIN_YEARS_OF_SERVICE", ">=", "5");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        var failed = eligibility.checkEligibility(101, 1002);
        assertThat(failed.eligible()).isFalse();
        assertThat(failed.reasons()).hasSize(4);
    }

    @Test void usesActualOfficerDepartmentNotNominatingDepartment() {
        rule("DEPARTMENT", "IN", "10");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        assertThatThrownBy(() -> nominate(1002, 10)).isInstanceOf(EligibilityException.class);
        assertThat(nominations.capacitySummary(101).confirmedCount()).isZero();
        assertThat(nominations.capacitySummary(101).waitingListCount()).isZero();
        long alternate = rule("DEPARTMENT", "IN", "1,2,3");
        assertThat(eligibility.checkEligibility(101, 1002).eligible()).isFalse();
        rules.update(alternate, new EligibilityRuleRequest("DEPARTMENT", "IN", "20", true));
        assertThat(eligibility.checkEligibility(101, 1002).eligible()).isTrue();
    }

    @Test void missingAttributesFailOnlyApplicableRulesAndServiceUsesCompletedYears() {
        rule("MIN_YEARS_OF_SERVICE", ">=", "5");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        jdbc.update("UPDATE officer SET date_of_joining = '2021-09-10' WHERE officer_id = 1001");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isFalse();
        jdbc.update("UPDATE officer SET date_of_joining = '2027-01-01' WHERE officer_id = 1001");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isFalse();
        rule("GRADE", "=", "Grade I");
        rule("DESIGNATION", "=", "Manager");
        rule("DEPARTMENT", "IN", "Finance");
        assertThat(eligibility.checkEligibility(101, 1003).reasons()).hasSize(4);
    }

    @Test void informationalApprovalDoesNotBypassPostAndRejectedOfficersNeverEnterQueue() throws Exception {
        mvc.perform(get("/api/trainings/101/eligibility/1002")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));
        rule("DESIGNATION", "=", "Manager");
        nominate(1001, 10); // All seats full.
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1002,\"nominatedByDepartmentId\":20,\"eligible\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OFFICER_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.message").value("Officer does not meet the eligibility requirements."))
                .andExpect(jsonPath("$.reasons[0]").isNotEmpty());
        assertThat(nominations.capacitySummary(101).confirmedCount()).isEqualTo(1);
        assertThat(nominations.capacitySummary(101).waitingListCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_nomination WHERE officer_id = 1002", Long.class)).isZero();
    }

    @Test void duplicateHasPriorityOverNewEligibilityRules() throws Exception {
        nominate(1001, 10);
        rule("GRADE", "=", "Grade III");
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1001,\"nominatedByDepartmentId\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NOMINATION"));
    }

    @Test void historyUsesConfiguredMonthsAndOnlyPastConfirmedParticipation() {
        long historyRule = rule("NO_SAME_TRAINING_WITHIN_MONTHS", "=", "12");
        jdbc.update("""
                INSERT INTO training_nomination (training_id, officer_id, nominated_by_department_id, nomination_datetime, status)
                VALUES (101, 1001, 10, '2026-03-09 10:00:00', 'CONFIRMED')
                """);
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isFalse();
        rules.update(historyRule, new EligibilityRuleRequest("NO_SAME_TRAINING_WITHIN_MONTHS", "=", "3", true));
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        rules.update(historyRule, new EligibilityRuleRequest("NO_SAME_TRAINING_WITHIN_MONTHS", "=", "12", true));
        for (String status : List.of("CANCELLED", "WAITING_LIST")) {
            jdbc.update("UPDATE training_nomination SET status = ?", status);
            assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        }
        jdbc.update("UPDATE training_nomination SET status = 'CONFIRMED'");
        jdbc.update("UPDATE training_programme SET training_date = '2026-10-15' WHERE training_id = 101");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        jdbc.update("UPDATE training_programme SET training_date = '2025-09-09' WHERE training_id = 101");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isFalse(); // Inclusive boundary.
        jdbc.update("UPDATE training_programme SET training_date = '2025-09-08' WHERE training_id = 101");
        assertThat(eligibility.checkEligibility(101, 1001).eligible()).isTrue();
        assertThat(eligibility.checkEligibility(102, 1001).eligible()).isTrue();
    }

    @Test void ruleCrudImmediatelyChangesEligibility() throws Exception {
        var response = mvc.perform(post("/api/trainings/101/eligibility-rules").contentType("application/json")
                .content("{\"ruleType\":\"MIN_YEARS_OF_SERVICE\",\"operator\":\">=\",\"ruleValue\":\"3\",\"isActive\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isActive").value(true)).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("ruleId").asLong();
        assertThat(eligibility.checkEligibility(101, 1002).eligible()).isTrue();
        mvc.perform(put("/api/eligibility-rules/" + id).contentType("application/json")
                .content("{\"ruleType\":\"MIN_YEARS_OF_SERVICE\",\"operator\":\">=\",\"ruleValue\":\"5\",\"isActive\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/trainings/101/eligibility/1002")).andExpect(status().isOk())
                .andExpect(jsonPath("$.trainingId").value(101)).andExpect(jsonPath("$.officerId").value(1002))
                .andExpect(jsonPath("$.eligible").value(false)).andExpect(jsonPath("$.reasons[0]").value(
                        "Minimum 5 years of service is required; a valid date of joining must be recorded."));
        mvc.perform(delete("/api/eligibility-rules/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/eligibility-rules/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trainings/101/eligibility-rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(false));
        assertThat(eligibility.checkEligibility(101, 1002).eligible()).isTrue();
    }

    @Test void rejectsInvalidRulesAndMissingResources() throws Exception {
        for (String[] invalid : List.of(
                new String[]{"UNKNOWN", "=", "x"}, new String[]{"GRADE", ">", "Grade I"},
                new String[]{"MIN_YEARS_OF_SERVICE", ">=", "-1"}, new String[]{"MIN_YEARS_OF_SERVICE", ">=", "2.5"},
                new String[]{"NO_SAME_TRAINING_WITHIN_MONTHS", "=", "0"},
                new String[]{"NO_SAME_TRAINING_WITHIN_MONTHS", "=", "9999999999999"},
                new String[]{"DEPARTMENT", "IN", "Finance,,Budget"})) {
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    new EligibilityRuleRequest(invalid[0], invalid[1], invalid[2], true));
            mvc.perform(post("/api/trainings/101/eligibility-rules").contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ELIGIBILITY_RULE"));
        }
        mvc.perform(post("/api/trainings/101/eligibility-rules").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trainings/101/eligibility/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trainings/999/eligibility/1001")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trainings/999/eligibility-rules")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/eligibility-rules/999")).andExpect(status().isNotFound());
    }

    @Test void directlyInsertedInvalidActiveRuleFailsClosed() throws Exception {
        rule("DEPARTMENT", "IN", "Finance");
        jdbc.update("""
                INSERT INTO training_eligibility_rule (training_id, rule_type, `operator`, rule_value, is_active)
                VALUES (101, 'MIN_YEARS_OF_SERVICE', '>=', 'not-a-number', TRUE)
                """);
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1001,\"nominatedByDepartmentId\":10}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ELIGIBILITY_CONFIGURATION"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_nomination", Long.class)).isZero();
    }
}

