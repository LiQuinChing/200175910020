package gov.training.controller;

import gov.training.dto.CreateNominationRequest;
import gov.training.model.TrainingNomination;
import gov.training.service.NominationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nominations")
public class NominationController {
    private final NominationService service;
    public NominationController(NominationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TrainingNomination> create(@Valid @RequestBody CreateNominationRequest request) {
        var nomination = service.create(request);
        return ResponseEntity.created(URI.create("/api/nominations/" + nomination.nominationId())).body(nomination);
    }

    @GetMapping("/{id}")
    public TrainingNomination get(@PathVariable @Positive long id) { return service.get(id); }

    @GetMapping
    public List<TrainingNomination> list(@RequestParam(required = false) @Positive Long trainingId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return service.list(trainingId, limit, offset);
    }
}

