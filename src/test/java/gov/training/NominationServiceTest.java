package gov.training;
import gov.training.dto.CreateNominationRequest;
import gov.training.exception.DuplicateNominationException;
import gov.training.repository.NominationRepository;
import gov.training.service.NominationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NominationServiceTest {
    @Test void translatesConstraintViolationWhenPrecheckPasses() {
        var repository = mock(NominationRepository.class);
        when(repository.exists(101, 1001)).thenReturn(false);
        when(repository.insert(101, 1001, 20)).thenThrow(new DuplicateKeyException("uq_training_officer"));
        assertThatThrownBy(() -> new NominationService(repository).create(new CreateNominationRequest(101L, 1001L, 20L)))
                .isInstanceOf(DuplicateNominationException.class)
                .hasMessage("Officer is already nominated for this training programme.");
    }
}

