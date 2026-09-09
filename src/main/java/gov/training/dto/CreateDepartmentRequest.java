package gov.training.dto;
import jakarta.validation.constraints.*;
public record CreateDepartmentRequest(@NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, digits, underscores or hyphens") String code) {}

