package gov.training.repository;
import gov.training.model.Department;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {
    private final JdbcTemplate jdbc;
    public DepartmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Department> findAll() {
        return jdbc.query("SELECT department_id, department_name, code FROM department ORDER BY department_id",
                (rs, row) -> new Department(rs.getLong("department_id"), rs.getString("department_name"), rs.getString("code")));
    }

    public Department insert(String name, String code) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO department (department_name, code) VALUES (?, ?)", new String[] {"department_id"});
            statement.setString(1, name);
            statement.setString(2, code);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a department ID.");
        return new Department(key.longValue(), name, code);
    }
}

