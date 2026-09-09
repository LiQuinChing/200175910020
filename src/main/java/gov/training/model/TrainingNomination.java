package gov.training.model;
import java.time.Instant;

public record TrainingNomination(long nominationId, long trainingId, long officerId,
        long nominatedByDepartmentId, Instant nominationDateTime, NominationStatus status,
        String officerName, String trainingName, String departmentName) {}
