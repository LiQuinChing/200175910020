package gov.training.repository;
import gov.training.model.Officer;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OfficerRepository {
    private final JdbcTemplate jdbc;
    public OfficerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Officer> findById(long officerId) {
        return jdbc.query("""
                SELECT o.*, d.department_name FROM officer o
                LEFT JOIN department d ON d.department_id = o.department_id
                WHERE o.officer_id = ?
                """, (rs, row) -> new Officer(rs.getLong("officer_id"), rs.getString("officer_name"),
                        rs.getObject("department_id", Long.class), rs.getString("designation"), rs.getString("email"),
                        rs.getString("grade"), rs.getObject("date_of_joining", java.time.LocalDate.class),
                        rs.getString("department_name")), officerId).stream().findFirst();
    }
    public boolean exists(long officerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM officer WHERE officer_id = ?)", Boolean.class, officerId));
    }
}
