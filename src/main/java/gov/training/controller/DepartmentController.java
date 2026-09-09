package gov.training.controller;
import gov.training.dto.CreateDepartmentRequest;
import gov.training.model.Department;
import gov.training.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    private final DepartmentService service;
    public DepartmentController(DepartmentService service) { this.service = service; }
    @GetMapping
    public List<Department> list() { return service.list(); }
    @PostMapping
    public ResponseEntity<Department> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}

