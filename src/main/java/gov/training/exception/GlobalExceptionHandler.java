package gov.training.exception;

import gov.training.dto.ApiError;
import gov.training.dto.EligibilityError;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EligibilityException.class)
    public ResponseEntity<EligibilityError> eligibility(EligibilityException ex) {
        return ResponseEntity.badRequest().body(new EligibilityError(Instant.now(), 400,
                "OFFICER_NOT_ELIGIBLE", ex.getMessage(), ex.getReasons()));
    }
    @ExceptionHandler(NominationOperationException.class)
    public ResponseEntity<ApiError> nominationOperation(NominationOperationException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicateCode(DuplicateKeyException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_DEPARTMENT_CODE", "A department with this code already exists.");
    }

    @ExceptionHandler(DuplicateNominationException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateNominationException ex) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_NOMINATION", ex.getMessage());
    }

    @ExceptionHandler(NominationNotFoundException.class)
    public ResponseEntity<ApiError> missing(NominationNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOMINATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).sorted().collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> malformed(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Provide a valid JSON body and valid positive IDs; limit must be 1–200 and offset nonnegative.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE",
                "Training programme, officer, and nominating department must exist.");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message));
    }
}
