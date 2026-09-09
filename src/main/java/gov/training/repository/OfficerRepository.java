package gov.training.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OfficerRepository {
    private final JdbcTemplate jdbc;
    public OfficerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean exists(long officerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM officer WHERE officer_id = ?)", Boolean.class, officerId));
    }
}

