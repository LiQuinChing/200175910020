package gov.training.repository;
import gov.training.model.TrainingProgramme;
import gov.training.dto.CreateTrainingRequest;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingRepository {
    private final JdbcTemplate jdbc;
    public TrainingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void updateCapacity(long trainingId, int capacity) {
        jdbc.update("UPDATE training_programme SET max_participants = ? WHERE training_id = ?", capacity, trainingId);
    }

    public Optional<TrainingProgramme> lockTrainingProgramme(long trainingId) {
        return jdbc.query("SELECT * FROM training_programme WHERE training_id = ? FOR UPDATE",
                (rs, row) -> new TrainingProgramme(rs.getLong("training_id"), rs.getString("training_name"),
                        rs.getObject("training_date", java.time.LocalDate.class), rs.getString("venue"),
                        rs.getObject("max_participants", Integer.class)), trainingId).stream().findFirst();
    }

    public List<TrainingProgramme> findAll() {
        return jdbc.query("SELECT * FROM training_programme ORDER BY training_id", (rs, row) ->
                new TrainingProgramme(rs.getLong("training_id"), rs.getString("training_name"),
                        rs.getObject("training_date", java.time.LocalDate.class), rs.getString("venue"),
                        rs.getObject("max_participants", Integer.class)));
    }

    public TrainingProgramme insert(CreateTrainingRequest request) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO training_programme (training_name, training_date, venue, max_participants) VALUES (?, ?, ?, ?)",
                    new String[] {"training_id"});
            statement.setString(1, request.title().trim());
            statement.setDate(2, Date.valueOf(request.trainingDate()));
            statement.setString(3, request.venue().trim());
            statement.setInt(4, request.maxParticipants());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Database did not return a training ID.");
        return new TrainingProgramme(key.longValue(), request.title().trim(), request.trainingDate(),
                request.venue().trim(), request.maxParticipants());
    }
}
