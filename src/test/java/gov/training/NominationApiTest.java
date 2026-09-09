package gov.training;

import gov.training.repository.NominationRepository;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:nominations;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class NominationApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NominationRepository repository;

    @BeforeEach void seed() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        // Execute the actual MySQL table definitions, omitting only database selection and engine.
        String schema = Files.readString(Path.of("schema.sql"));
        for (String statement : schema.split(";")) {
            if (statement.stripLeading().startsWith("CREATE TABLE")) {
                jdbc.execute(statement.replace("ENGINE=InnoDB", ""));
            }
        }
        jdbc.update("INSERT INTO department (department_id, department_name, code) VALUES (10, 'Finance', 'FIN'), (20, 'Administration', 'ADM')");
        jdbc.update("INSERT INTO officer (officer_id, officer_name) VALUES (1001, 'Same Name'), (1002, 'Same Name')");
        jdbc.update("INSERT INTO training_programme (training_id, training_name, max_participants) VALUES (101, 'Training A', 40), (102, 'Training B', 40)");
    }

    private org.springframework.test.web.servlet.ResultActions nominate(long training, long officer, long department) throws Exception {
        return mvc.perform(post("/api/nominations").contentType("application/json").content(
                "{\"trainingId\":" + training + ",\"officerId\":" + officer + ",\"nominatedByDepartmentId\":" + department + "}"));
    }

    @Test void createsAndRetrieves() throws Exception {
        nominate(101, 1001, 10).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/nominations/1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.nominationDateTime").isNotEmpty());
        mvc.perform(get("/api/nominations/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(1001))
                .andExpect(jsonPath("$.officerName").value("Same Name"))
                .andExpect(jsonPath("$.trainingName").value("Training A"))
                .andExpect(jsonPath("$.departmentName").value("Finance"));
        mvc.perform(get("/api/nominations?trainingId=101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/nominations?trainingId=102")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void preventsDuplicatesAcrossDepartments() throws Exception {
        nominate(101, 1001, 10).andExpect(status().isCreated());
        nominate(101, 1001, 10).andExpect(status().isConflict());
        nominate(101, 1001, 20).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Officer is already nominated for this training programme."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_nomination", Integer.class)).isEqualTo(1);
    }

    @Test void acceptsFrontendDepartmentIdAndRejectsDuplicate() throws Exception {
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1001,\"departmentId\":10}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nominatedByDepartmentId").value(10));
        mvc.perform(post("/api/nominations").contentType("application/json")
                .content("{\"trainingId\":101,\"officerId\":1001,\"departmentId\":20}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NOMINATION"));
    }

    @Test void uniquenessUsesIdsAndAllowsOtherTraining() throws Exception {
        nominate(101, 1001, 10).andExpect(status().isCreated());
        nominate(101, 1002, 10).andExpect(status().isCreated());
        nominate(102, 1001, 20).andExpect(status().isCreated());
    }

    @Test void createsMasterDataAndUsesItForNominations() throws Exception {
        var departmentResult = mvc.perform(post("/api/departments").contentType("application/json")
                .content("{\"name\":\"Finance Division\",\"code\":\"fnd\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("FND"))
                .andReturn();
        var trainingResult = mvc.perform(post("/api/trainings").contentType("application/json")
                .content("{\"title\":\"Cyber Security\",\"trainingDate\":\"2026-10-15\",\"venue\":\"Main Auditorium\",\"maxParticipants\":75}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.trainingDate").value("2026-10-15"))
                .andExpect(jsonPath("$.maxParticipants").value(75)).andReturn();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        long departmentId = json.readTree(departmentResult.getResponse().getContentAsString()).get("departmentId").asLong();
        long trainingId = json.readTree(trainingResult.getResponse().getContentAsString()).get("trainingId").asLong();
        mvc.perform(get("/api/departments")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'FND')].departmentId").value(org.hamcrest.Matchers.hasItem((int) departmentId)));
        mvc.perform(get("/api/trainings")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Cyber Security')].trainingId").value(org.hamcrest.Matchers.hasItem((int) trainingId)));
        nominate(trainingId, 1001, departmentId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentName").value("Finance Division"))
                .andExpect(jsonPath("$.trainingName").value("Cyber Security"));
        nominate(trainingId, 1001, 20).andExpect(status().isConflict());
    }

    @Test void validatesManagementInputAndUniqueDepartmentCodes() throws Exception {
        mvc.perform(post("/api/departments").contentType("application/json")
                .content("{\"name\":\"Duplicate\",\"code\":\"fin\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_DEPARTMENT_CODE"));
        mvc.perform(post("/api/departments").contentType("application/json")
                .content("{\"name\":\"   \",\"code\":\"NEW\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/departments").contentType("application/json")
                .content("{\"name\":\"New\",\"code\":\"bad code\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/trainings").contentType("application/json")
                .content("{\"title\":\"Training\",\"trainingDate\":\"invalid\",\"venue\":\"Room\",\"maxParticipants\":75}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trainings").contentType("application/json")
                .content("{\"title\":\"Training\",\"trainingDate\":\"2026-10-15\",\"venue\":\"Room\",\"maxParticipants\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trainings").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidRequestsAndReferences() throws Exception {
        nominate(101, 9999, 10).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFICER_NOT_FOUND"));
        nominate(9999, 1001, 10).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRAINING_NOT_FOUND"));
        nominate(101, 1001, 9999).andExpect(status().isBadRequest());
        nominate(0, 1001, 10).andExpect(status().isBadRequest());
        mvc.perform(post("/api/nominations").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/nominations").contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/nominations/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/nominations?limit=201")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/nominations?offset=-1")).andExpect(status().isBadRequest());
    }

    @Test void databaseProtectsConcurrentInsertsAfterBothPrechecksPass() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> first = () -> insertAfterCheck(10, barrier);
            Callable<Boolean> second = () -> insertAfterCheck(20, barrier);
            var a = pool.submit(first);
            var b = pool.submit(second);
            assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_nomination", Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    private boolean insertAfterCheck(long department, CyclicBarrier barrier) throws Exception {
        assertThat(repository.exists(101, 1001)).isFalse();
        barrier.await(10, TimeUnit.SECONDS);
        try {
            repository.insert(101, 1001, department, gov.training.model.NominationStatus.CONFIRMED);
            return true;
        } catch (DuplicateKeyException ex) { return false; }
    }
}
