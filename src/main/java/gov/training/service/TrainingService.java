package gov.training.service;
import gov.training.dto.CreateTrainingRequest;
import gov.training.model.TrainingProgramme;
import gov.training.repository.TrainingRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingService {
    private final TrainingRepository repository;
    public TrainingService(TrainingRepository repository) { this.repository = repository; }
    public List<TrainingProgramme> list() { return repository.findAll(); }
    @Transactional
    public TrainingProgramme create(CreateTrainingRequest request) { return repository.insert(request); }
}

