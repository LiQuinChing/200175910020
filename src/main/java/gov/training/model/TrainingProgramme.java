package gov.training.model;
import java.time.LocalDate;
public record TrainingProgramme(long trainingId, String title, LocalDate trainingDate,
        String venue, Integer maxParticipants) {}

