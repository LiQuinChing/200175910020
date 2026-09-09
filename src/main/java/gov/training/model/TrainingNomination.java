package gov.training.model;
import java.time.Instant;

public record TrainingNomination(long nominationId, long trainingId, long officerId,
        long nominatedByDepartmentId, Instant nominationDate, NominationStatus status,
        String officerName, String trainingName, String departmentName) {}
