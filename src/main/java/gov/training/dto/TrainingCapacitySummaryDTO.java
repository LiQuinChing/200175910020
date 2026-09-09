package gov.training.dto;

public record TrainingCapacitySummaryDTO(long trainingId, String title, int maxParticipants,
        long confirmedCount, long waitingListCount, long cancelledCount, long availableSeats) {}

