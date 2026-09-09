package gov.training;

import gov.training.dto.CreateNominationRequest;
import gov.training.exception.NominationOperationException;
import gov.training.model.*;
import gov.training.service.NominationService;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static gov.training.model.NominationStatus.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:capacity;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class TrainingCapacityTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NominationService service;
    @Autowired MockMvc mvc;

    @BeforeEach void seed() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        for (String statement : Files.readString(Path.of("schema.sql")).split(";")) {
            if (statement.stripLeading().startsWith("CREATE TABLE")) {
                jdbc.execute(statement.replace("ENGINE=InnoDB", ""));
            }
        }
        jdbc.update("INSERT INTO department (department_id, department_name, code) VALUES (10, 'Finance', 'FIN'), (20, 'Admin', 'ADM')");
        jdbc.update("INSERT INTO training_programme (training_id, training_name, max_participants) VALUES (101, 'Cybersecurity', 1), (102, 'Other', 1)");
        for (int i = 1001; i <= 1045; i++) {
            jdbc.update("INSERT INTO officer (officer_id, officer_name) VALUES (?, ?)", i, "Officer " + i);
        }
    }

    private TrainingNomination create(long officer) {
        return service.create(new CreateNominationRequest(101L, officer, 10L));
    }

    @Test void allocatesSeatsAndReturnsSummaryAndOrderedList() throws Exception {
        var confirmed = create(1001);
        var firstWaiting = create(1002);
        var secondWaiting = create(1003);
        assertThat(confirmed.status()).isEqualTo(CONFIRMED);
        assertThat(firstWaiting.status()).isEqualTo(WAITING_LIST);
        assertThat(firstWaiting.nominationDateTime()).isNotNull();
        service.cancel(firstWaiting.nominationId());
        mvc.perform(get("/api/trainings/101/capacity-summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cybersecurity"))
                .andExpect(jsonPath("$.maxParticipants").value(1))
                .andExpect(jsonPath("$.confirmedCount").value(1))
                .andExpect(jsonPath("$.waitingListCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.availableSeats").value(0));
        mvc.perform(get("/api/trainings/101/nominations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[1].nominationId").value(secondWaiting.nominationId()))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].officerName").value("Officer 1001"))
                .andExpect(jsonPath("$[0].departmentName").value("Finance"))
                .andExpect(jsonPath("$[0].nominationDateTime").isNotEmpty());
    }

    @Test void cancellationPromotesByTimestampThenIdAndPreservesSubmissionTime() throws Exception {
        var confirmed = create(1001);
        var earlyId = create(1002);
        var lateId = create(1003);
        // A later ID with an earlier timestamp wins, proving timestamp is the primary key.
        jdbc.update("UPDATE training_nomination SET nomination_datetime = '2026-09-09 10:00:00.123456' WHERE nomination_id = ?", lateId.nominationId());
        var originalTime = service.get(lateId.nominationId()).nominationDateTime();
        mvc.perform(put("/api/nominations/" + confirmed.nominationId() + "/cancel"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(service.get(lateId.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.get(lateId.nominationId()).nominationDateTime()).isEqualTo(originalTime);
        assertThat(service.get(earlyId.nominationId()).status()).isEqualTo(WAITING_LIST);
        var another = create(1004);
        jdbc.update("UPDATE training_nomination SET nomination_datetime = '2026-09-09 11:00:00.123456' WHERE status = 'WAITING_LIST'");
        service.cancel(lateId.nominationId());
        assertThat(service.get(earlyId.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.get(another.nominationId()).status()).isEqualTo(WAITING_LIST);
    }

    @Test void cancellingWaitingDoesNotPromoteAndDuplicateSurvivesCancellation() throws Exception {
        var confirmed = create(1001);
        var waiting = create(1002);
        create(1003);
        service.cancel(waiting.nominationId());
        assertThat(service.get(confirmed.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.capacitySummary(101).confirmedCount()).isEqualTo(1);
        mvc.perform(put("/api/nominations/" + waiting.nominationId() + "/cancel"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1002,\"nominatedByDepartmentId\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NOMINATION"));
    }

    @Test void cancellationWithoutQueueLeavesSeatAvailableAndDoesNotPromoteOtherTraining() {
        var confirmed = create(1001);
        service.create(new CreateNominationRequest(102L, 1002L, 10L));
        var otherWaiting = service.create(new CreateNominationRequest(102L, 1003L, 10L));
        service.cancel(confirmed.nominationId());
        assertThat(service.capacitySummary(101).availableSeats()).isEqualTo(1);
        assertThat(service.get(otherWaiting.nominationId()).status()).isEqualTo(WAITING_LIST);
        assertThat(create(1004).status()).isEqualTo(CONFIRMED);
    }

    @Test void failedPromotionRollsBackCancellation() {
        var confirmed = create(1001);
        var waiting = create(1002);
        jdbc.execute("ALTER TABLE training_nomination ADD CONSTRAINT simulate_promotion_failure CHECK (NOT (officer_id = 1002 AND status = 'CONFIRMED'))");
        assertThatThrownBy(() -> service.cancel(confirmed.nominationId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.get(confirmed.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.get(waiting.nominationId()).status()).isEqualTo(WAITING_LIST);
    }

    @Test void errorsAndSummaryNeverNegative() throws Exception {
        mvc.perform(put("/api/nominations/999/cancel")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trainings/999/nominations")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trainings/999/capacity-summary")).andExpect(status().isNotFound());
        mvc.perform(get("/api/trainings/102/capacity-summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(0)).andExpect(jsonPath("$.availableSeats").value(1));
        jdbc.update("UPDATE training_programme SET max_participants = NULL WHERE training_id = 101");
        assertThatThrownBy(() -> create(1001)).isInstanceOf(NominationOperationException.class)
                .hasMessageContaining("positive maximum");
        jdbc.update("UPDATE training_programme SET max_participants = 2 WHERE training_id = 101");
        create(1001);
        create(1002);
        jdbc.update("UPDATE training_programme SET max_participants = 1 WHERE training_id = 101");
        assertThat(service.capacitySummary(101).availableSeats()).isZero();
    }

    @Test void concurrentLastSeatCannotOverbook() throws Exception {
        jdbc.update("UPDATE training_programme SET max_participants = 40 WHERE training_id = 101");
        for (long officer = 1001; officer < 1040; officer++) create(officer);
        List<NominationStatus> results = race(
                () -> create(1040).status(), () -> create(1041).status());
        assertThat(results).containsExactlyInAnyOrder(CONFIRMED, WAITING_LIST);
        assertThat(service.capacitySummary(101).confirmedCount()).isEqualTo(40);
        assertThat(service.capacitySummary(101).waitingListCount()).isEqualTo(1);
    }

    @Test void capacityEditsPromoteInOrderAndRejectOverbooking() throws Exception {
        create(1001);
        var waiting = create(1002);
        var last = create(1003);
        mvc.perform(put("/api/trainings/101/capacity").contentType("application/json")
                .content("{\"maxParticipants\":2}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.waitingListCount").value(1));
        assertThat(service.get(waiting.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.get(last.nominationId()).status()).isEqualTo(WAITING_LIST);
        mvc.perform(put("/api/trainings/101/capacity").contentType("application/json")
                .content("{\"maxParticipants\":1}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_CONFIRMED"));
        assertThat(service.capacitySummary(101).maxParticipants()).isEqualTo(2);
        mvc.perform(put("/api/trainings/101/capacity").contentType("application/json")
                .content("{\"maxParticipants\":0}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE training_programme SET max_participants = NULL WHERE training_id = 102");
        mvc.perform(put("/api/trainings/102/capacity").contentType("application/json")
                .content("{\"maxParticipants\":40}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(40));
    }

    @Test void simultaneousCancellationsCannotPromoteTwice() throws Exception {
        var confirmed = create(1001);
        var firstWaiting = create(1002);
        var secondWaiting = create(1003);
        Callable<Boolean> cancel = () -> {
            try { service.cancel(confirmed.nominationId()); return true; }
            catch (NominationOperationException ex) {
                assertThat(ex.getCode()).isEqualTo("ALREADY_CANCELLED");
                return false;
            }
        };
        assertThat(race(cancel, cancel)).containsExactlyInAnyOrder(true, false);
        assertThat(service.get(firstWaiting.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.get(secondWaiting.nominationId()).status()).isEqualTo(WAITING_LIST);
    }

    @Test void concurrentCreateAndCancelHonoursExistingQueue() throws Exception {
        var confirmed = create(1001);
        var waiting = create(1002);
        race(() -> service.cancel(confirmed.nominationId()), () -> create(1003));
        assertThat(service.get(waiting.nominationId()).status()).isEqualTo(CONFIRMED);
        assertThat(service.capacitySummary(101).confirmedCount()).isEqualTo(1);
        assertThat(service.capacitySummary(101).waitingListCount()).isEqualTo(1);
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var a = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
