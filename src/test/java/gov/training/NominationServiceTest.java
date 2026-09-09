package gov.training;
import gov.training.dto.CreateNominationRequest;
import gov.training.exception.DuplicateNominationException;
import gov.training.repository.NominationRepository;
import gov.training.repository.TrainingRepository;
import gov.training.repository.OfficerRepository;
import gov.training.model.TrainingProgramme;
import gov.training.model.NominationStatus;
import java.util.Optional;
import gov.training.service.NominationService;
import gov.training.service.EligibilityService;
import gov.training.dto.EligibilityResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NominationServiceTest {
    @Test void translatesConstraintViolationWhenPrecheckPasses() {
        var repository = mock(NominationRepository.class);
        var trainings = mock(TrainingRepository.class);
        var officers = mock(OfficerRepository.class);
        var eligibility = mock(EligibilityService.class);
        when(eligibility.checkEligibility(101, 1001)).thenReturn(new EligibilityResult(101, 1001, true, List.of()));
        when(trainings.lockTrainingProgramme(101)).thenReturn(Optional.of(new TrainingProgramme(101, "Test", null, null, 40)));
        when(officers.exists(1001)).thenReturn(true);
        when(repository.exists(101, 1001)).thenReturn(false);
        when(repository.insert(101, 1001, 20, NominationStatus.CONFIRMED)).thenThrow(new DuplicateKeyException("uq_training_officer"));
        assertThatThrownBy(() -> new NominationService(repository, trainings, officers, eligibility).create(new CreateNominationRequest(101L, 1001L, 20L)))
                .isInstanceOf(DuplicateNominationException.class)
                .hasMessage("Officer is already nominated for this training programme.");
    }
}
