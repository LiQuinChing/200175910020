package gov.training.service;

import gov.training.dto.CreateNominationRequest;
import gov.training.exception.*;
import gov.training.model.TrainingNomination;
import gov.training.repository.NominationRepository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NominationService {
    private final NominationRepository repository;
    public NominationService(NominationRepository repository) { this.repository = repository; }

    @Transactional
    public TrainingNomination create(CreateNominationRequest request) {
        if (repository.exists(request.trainingId(), request.officerId())) {
            throw new DuplicateNominationException();
        }
        try {
            return repository.insert(request.trainingId(), request.officerId(), request.nominatedByDepartmentId());
        } catch (DuplicateKeyException ex) {
            // The UNIQUE(training_id, officer_id) constraint arbitrates concurrent inserts.
            throw new DuplicateNominationException();
        }
    }

    public TrainingNomination get(long id) {
        return repository.findById(id).orElseThrow(() -> new NominationNotFoundException(id));
    }

    public List<TrainingNomination> list(Long trainingId, int limit, int offset) {
        return repository.findAll(trainingId, limit, offset);
    }
}

