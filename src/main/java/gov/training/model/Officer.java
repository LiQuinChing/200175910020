package gov.training.model;
import java.time.LocalDate;
public record Officer(long officerId, String name, Long departmentId, String designation,
        String email, String grade, LocalDate dateOfJoining, String departmentName) {}
