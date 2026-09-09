package gov.training.repository;

import gov.training.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class NominationRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT n.*, o.officer_name, t.training_name, d.department_name
            FROM training_nomination n
            JOIN officer o ON o.officer_id = n.officer_id
            JOIN training_programme t ON t.training_id = n.training_id
            JOIN department d ON d.department_id = n.nominated_by_department_id
            """;
    private static final RowMapper<TrainingNomination> MAPPER = (rs, row) -> new TrainingNomination(
            rs.getLong("nomination_id"), rs.getLong("training_id"), rs.getLong("officer_id"),
            rs.getLong("nominated_by_department_id"), rs.getTimestamp("nomination_date").toInstant(),
            NominationStatus.valueOf(rs.getString("status")), rs.getString("officer_name"),
            rs.getString("training_name"), rs.getString("department_name"));

    public NominationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean exists(long trainingId, long officerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM training_nomination WHERE training_id = ? AND officer_id = ?)",
                Boolean.class, trainingId, officerId));
    }

    public TrainingNomination insert(long trainingId, long officerId, long departmentId) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO training_nomination (training_id, officer_id, nominated_by_department_id, status) VALUES (?, ?, ?, 'NOMINATED')",
                    new String[] { "nomination_id" });
            statement.setLong(1, trainingId);
            statement.setLong(2, officerId);
            statement.setLong(3, departmentId);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a nomination ID.");
        return findById(key.longValue()).orElseThrow();
    }

    public Optional<TrainingNomination> findById(long id) {
        return jdbc.query(SELECT + " WHERE n.nomination_id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public List<TrainingNomination> findAll(Long trainingId, int limit, int offset) {
        if (trainingId != null) {
            return jdbc.query(SELECT + " WHERE n.training_id = ? ORDER BY n.nomination_id LIMIT ? OFFSET ?",
                    MAPPER, trainingId, limit, offset);
        }
        return jdbc.query(SELECT + " ORDER BY n.nomination_id LIMIT ? OFFSET ?",
                MAPPER, limit, offset);
    }
}
