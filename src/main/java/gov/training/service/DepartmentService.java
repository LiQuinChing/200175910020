package gov.training.service;
import gov.training.dto.CreateDepartmentRequest;
import gov.training.model.Department;
import gov.training.repository.DepartmentRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {
    private final DepartmentRepository repository;
    public DepartmentService(DepartmentRepository repository) { this.repository = repository; }
    public List<Department> list() { return repository.findAll(); }
    @Transactional
    public Department create(CreateDepartmentRequest request) {
        return repository.insert(request.name().trim(), request.code().toUpperCase(Locale.ROOT));
    }
}

