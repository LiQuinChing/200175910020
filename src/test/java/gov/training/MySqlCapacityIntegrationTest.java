package gov.training;

import gov.training.dto.*;
import gov.training.model.*;
import gov.training.service.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static gov.training.model.NominationStatus.*;

/** Opt-in test against a migrated MySQL database; only its own generated fixtures are removed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_CAPACITY_TESTS", matches = "true")
class MySqlCapacityIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DepartmentService departments;
    @Autowired TrainingService trainings;
    @Autowired NominationService nominations;

    @Test void mysqlSerializesLastSeatAndPromotesWaitingOfficer() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        var department = departments.create(new CreateDepartmentRequest("Capacity verification", "TEST-" + suffix));
        TrainingProgramme training = null;
        long officerA = 1_000_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        long officerB = officerA + 1;
        boolean insertedA = false;
        boolean insertedB = false;
        var pool = Executors.newFixedThreadPool(2);
        try {
            training = trainings.create(new CreateTrainingRequest("Capacity verification " + suffix,
                    java.time.LocalDate.of(2026, 10, 15), "Test fixture", 1));
            jdbc.update("INSERT INTO officer (officer_id, officer_name, department_id) VALUES (?, 'Capacity test A', ?)",
                    officerA, department.departmentId());
            insertedA = true;
            jdbc.update("INSERT INTO officer (officer_id, officer_name, department_id) VALUES (?, 'Capacity test B', ?)",
                    officerB, department.departmentId());
            insertedB = true;
            long trainingId = training.trainingId();
            var barrier = new CyclicBarrier(2);
            var a = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return nominations.create(new CreateNominationRequest(trainingId, officerA, department.departmentId()));
            });
            var b = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return nominations.create(new CreateNominationRequest(trainingId, officerB, department.departmentId()));
            });
            var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(results).extracting(TrainingNomination::status).containsExactlyInAnyOrder(CONFIRMED, WAITING_LIST);
            var confirmed = results.stream().filter(n -> n.status() == CONFIRMED).findFirst().orElseThrow();
            var waiting = results.stream().filter(n -> n.status() == WAITING_LIST).findFirst().orElseThrow();
            nominations.cancel(confirmed.nominationId());
            assertThat(nominations.get(waiting.nominationId()).status()).isEqualTo(CONFIRMED);
            assertThat(nominations.capacitySummary(trainingId).confirmedCount()).isEqualTo(1);
            assertThat(nominations.capacitySummary(trainingId).cancelledCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Test workers did not stop.");
            if (training != null) {
                jdbc.update("DELETE FROM training_nomination WHERE training_id = ?", training.trainingId());
                jdbc.update("DELETE FROM training_programme WHERE training_id = ?", training.trainingId());
            }
            if (insertedA) jdbc.update("DELETE FROM officer WHERE officer_id = ?", officerA);
            if (insertedB) jdbc.update("DELETE FROM officer WHERE officer_id = ?", officerB);
            jdbc.update("DELETE FROM department WHERE department_id = ?", department.departmentId());
        }
    }
}
