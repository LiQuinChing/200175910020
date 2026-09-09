package gov.training.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record UpdateCapacityRequest(@NotNull @Positive Integer maxParticipants) {}
