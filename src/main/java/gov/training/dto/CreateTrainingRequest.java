package gov.training.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CreateTrainingRequest(@NotBlank @Size(max = 200) String title,
        @NotNull LocalDate trainingDate, @NotBlank @Size(max = 200) String venue,
        @NotNull @Positive Integer maxParticipants) {}

