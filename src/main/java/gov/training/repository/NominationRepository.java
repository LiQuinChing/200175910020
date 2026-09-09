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
            rs.getLong("nominated_by_department_id"), rs.getTimestamp("nomination_datetime").toInstant(),
            NominationStatus.valueOf(rs.getString("status")), rs.getString("officer_name"),
            rs.getString("training_name"), rs.getString("department_name"));

    public NominationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasParticipationSince(long trainingId, long officerId, java.time.LocalDate since, java.time.LocalDate today) {
        // CONFIRMED is the current participation proxy. Use the programme date when
        // available; otherwise use the historical nomination date. Future events do not count.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM training_nomination n
                    JOIN training_programme t ON t.training_id = n.training_id
                    WHERE n.training_id = ? AND n.officer_id = ?
                      AND n.status = 'CONFIRMED'
                      AND COALESCE(t.training_date, CAST(n.nomination_datetime AS DATE)) BETWEEN ? AND ?
                )
                """, Boolean.class, trainingId, officerId, java.sql.Date.valueOf(since), java.sql.Date.valueOf(today)));
    }

    public boolean exists(long trainingId, long officerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM training_nomination WHERE training_id = ? AND officer_id = ?)",
                Boolean.class, trainingId, officerId));
    }

    public TrainingNomination insert(long trainingId, long officerId, long departmentId, NominationStatus status) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO training_nomination (training_id, officer_id, nominated_by_department_id, status) VALUES (?, ?, ?, ?)",
                    new String[] { "nomination_id" });
            statement.setLong(1, trainingId);
            statement.setLong(2, officerId);
            statement.setLong(3, departmentId);
            statement.setString(4, status.name());
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

    public long countConfirmedByTraining(long trainingId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_nomination WHERE training_id = ? AND status = 'CONFIRMED'",
                Long.class, trainingId);
    }

    public int updateStatus(long nominationId, NominationStatus expected, NominationStatus status) {
        return jdbc.update("UPDATE training_nomination SET status = ? WHERE nomination_id = ? AND status = ?",
                status.name(), nominationId, expected.name());
    }

    public Optional<Long> findFirstWaitingNomination(long trainingId) {
        return jdbc.query("""
                SELECT nomination_id FROM training_nomination
                WHERE training_id = ? AND status = 'WAITING_LIST'
                ORDER BY nomination_datetime ASC, nomination_id ASC LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getLong("nomination_id"), trainingId).stream().findFirst();
    }

    public List<TrainingNomination> findNominationsByTraining(long trainingId) {
        return jdbc.query(SELECT + """
                 WHERE n.training_id = ?
                 ORDER BY CASE n.status WHEN 'CONFIRMED' THEN 0 WHEN 'WAITING_LIST' THEN 1 ELSE 2 END,
                 n.nomination_datetime ASC, n.nomination_id ASC
                """, MAPPER, trainingId);
    }

    public gov.training.dto.TrainingCapacitySummaryDTO getCapacitySummary(gov.training.model.TrainingProgramme training) {
        return jdbc.queryForObject("""
                SELECT COUNT(CASE WHEN status = 'CONFIRMED' THEN 1 END) AS confirmed_count,
                       COUNT(CASE WHEN status = 'WAITING_LIST' THEN 1 END) AS waiting_count,
                       COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count
                FROM training_nomination WHERE training_id = ?
                """, (rs, row) -> {
                    long confirmed = rs.getLong("confirmed_count");
                    return new gov.training.dto.TrainingCapacitySummaryDTO(
                            training.trainingId(), training.title(), training.maxParticipants(), confirmed,
                            rs.getLong("waiting_count"), rs.getLong("cancelled_count"),
                            Math.max(0L, training.maxParticipants() - confirmed));
                }, training.trainingId());
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
