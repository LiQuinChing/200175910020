package gov.training.service;

import gov.training.dto.CreateNominationRequest;
import gov.training.dto.TrainingCapacitySummaryDTO;
import gov.training.exception.*;
import gov.training.model.*;
import gov.training.repository.*;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NominationService {
    private final NominationRepository repository;
    private final TrainingRepository trainings;
    private final OfficerRepository officers;
    private final EligibilityService eligibility;
    public NominationService(NominationRepository repository, TrainingRepository trainings, OfficerRepository officers,
            EligibilityService eligibility) {
        this.repository = repository;
        this.trainings = trainings;
        this.officers = officers;
        this.eligibility = eligibility;
    }

    // All seat-changing operations acquire this same parent-row lock first.
    // READ_COMMITTED ensures the count sees the previous lock holder's committed insert.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrainingNomination create(CreateNominationRequest request) {
        if (!officers.exists(request.officerId())) {
            throw new NominationOperationException(HttpStatus.NOT_FOUND, "OFFICER_NOT_FOUND", "Officer was not found.");
        }
        TrainingProgramme training = lockTraining(request.trainingId());
        if (repository.exists(request.trainingId(), request.officerId())) {
            throw new DuplicateNominationException();
        }
        var result = eligibility.checkEligibility(request.trainingId(), request.officerId());
        if (!result.eligible()) throw new EligibilityException(result.reasons());
        int capacity = requireCapacity(training);
        NominationStatus status = repository.countConfirmedByTraining(training.trainingId()) < capacity
                ? NominationStatus.CONFIRMED : NominationStatus.WAITING_LIST;
        try {
            return repository.insert(request.trainingId(), request.officerId(), request.nominatedByDepartmentId(), status);
        } catch (DuplicateKeyException ex) {
            // The UNIQUE(training_id, officer_id) constraint arbitrates concurrent inserts.
            throw new DuplicateNominationException();
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrainingNomination cancel(long nominationId) {
        // This first read only discovers the immutable training ID; do not lock a child first.
        long trainingId = get(nominationId).trainingId();
        lockTraining(trainingId);
        TrainingNomination nomination = get(nominationId); // Re-read after acquiring the parent lock.
        if (nomination.status() == NominationStatus.CANCELLED) {
            throw new NominationOperationException(HttpStatus.CONFLICT, "ALREADY_CANCELLED",
                    "Nomination is already cancelled.");
        }
        if (nomination.status() != NominationStatus.CONFIRMED && nomination.status() != NominationStatus.WAITING_LIST) {
            throw invalidStatus();
        }
        if (repository.updateStatus(nominationId, nomination.status(), NominationStatus.CANCELLED) != 1) {
            throw invalidStatus();
        }
        if (nomination.status() == NominationStatus.CONFIRMED) {
            repository.findFirstWaitingNomination(trainingId).ifPresent(waitingId -> {
                if (repository.updateStatus(waitingId, NominationStatus.WAITING_LIST, NominationStatus.CONFIRMED) != 1) {
                    throw invalidStatus(); // Runtime exception rolls back cancellation and promotion together.
                }
            });
        }
        return get(nominationId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<TrainingNomination> listByTraining(long trainingId) {
        lockTraining(trainingId);
        return repository.findNominationsByTraining(trainingId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrainingCapacitySummaryDTO capacitySummary(long trainingId) {
        TrainingProgramme training = lockTraining(trainingId);
        requireCapacity(training);
        return repository.getCapacitySummary(training);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TrainingCapacitySummaryDTO updateCapacity(long trainingId, int capacity) {
        TrainingProgramme training = lockTraining(trainingId);
        if (capacity < 1) {
            throw new NominationOperationException(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY",
                    "Maximum participants must be positive.");
        }
        long confirmed = repository.countConfirmedByTraining(trainingId);
        if (capacity < confirmed) {
            throw new NominationOperationException(HttpStatus.CONFLICT, "CAPACITY_BELOW_CONFIRMED",
                    "Capacity cannot be lower than the current confirmed participant count.");
        }
        trainings.updateCapacity(trainingId, capacity);
        for (long seats = capacity - confirmed; seats > 0; seats--) {
            var waiting = repository.findFirstWaitingNomination(trainingId);
            if (waiting.isEmpty()) break;
            if (repository.updateStatus(waiting.get(), NominationStatus.WAITING_LIST, NominationStatus.CONFIRMED) != 1) {
                throw invalidStatus();
            }
        }
        return repository.getCapacitySummary(new TrainingProgramme(trainingId, training.title(),
                training.trainingDate(), training.venue(), capacity));
    }

    private TrainingProgramme lockTraining(long trainingId) {
        return trainings.lockTrainingProgramme(trainingId).orElseThrow(() ->
                new NominationOperationException(HttpStatus.NOT_FOUND, "TRAINING_NOT_FOUND",
                        "Training programme was not found."));
    }

    private int requireCapacity(TrainingProgramme training) {
        if (training.maxParticipants() == null || training.maxParticipants() < 1) {
            throw new NominationOperationException(HttpStatus.CONFLICT, "CAPACITY_NOT_CONFIGURED",
                    "Training programme must have a positive maximum participant capacity.");
        }
        return training.maxParticipants();
    }

    private NominationOperationException invalidStatus() {
        return new NominationOperationException(HttpStatus.CONFLICT, "INVALID_STATUS_OPERATION",
                "Nomination status does not allow this operation.");
    }

    public TrainingNomination get(long id) {
        return repository.findById(id).orElseThrow(() -> new NominationNotFoundException(id));
    }

    public List<TrainingNomination> list(Long trainingId, int limit, int offset) {
        return repository.findAll(trainingId, limit, offset);
    }
}
