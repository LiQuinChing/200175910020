package gov.training.controller;
import gov.training.dto.CreateTrainingRequest;
import gov.training.model.TrainingProgramme;
import gov.training.service.TrainingService;
import gov.training.service.NominationService;
import gov.training.model.TrainingNomination;
import gov.training.dto.TrainingCapacitySummaryDTO;
import gov.training.dto.UpdateCapacityRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trainings")
public class TrainingController {
    private final TrainingService service;
    private final NominationService nominations;
    public TrainingController(TrainingService service, NominationService nominations) {
        this.service = service;
        this.nominations = nominations;
    }
    @GetMapping("/{trainingId}/nominations")
    public List<TrainingNomination> nominations(@PathVariable @Positive long trainingId) {
        return nominations.listByTraining(trainingId);
    }
    @GetMapping("/{trainingId}/capacity-summary")
    public TrainingCapacitySummaryDTO capacitySummary(@PathVariable @Positive long trainingId) {
        return nominations.capacitySummary(trainingId);
    }
    @PutMapping("/{trainingId}/capacity")
    public TrainingCapacitySummaryDTO updateCapacity(@PathVariable @Positive long trainingId,
            @Valid @RequestBody UpdateCapacityRequest request) {
        return nominations.updateCapacity(trainingId, request.maxParticipants());
    }
    @GetMapping
    public List<TrainingProgramme> list() { return service.list(); }
    @PostMapping
    public ResponseEntity<TrainingProgramme> create(@Valid @RequestBody CreateTrainingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}
