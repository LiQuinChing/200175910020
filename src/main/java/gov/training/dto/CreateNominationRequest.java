package gov.training.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateNominationRequest(
        @NotNull @Positive Long trainingId,
        @NotNull @Positive Long officerId,
        @JsonAlias("departmentId") @NotNull @Positive Long nominatedByDepartmentId) {}
