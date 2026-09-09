package gov.training.exception;
public class DuplicateNominationException extends RuntimeException {
    public DuplicateNominationException() {
        super("Officer is already nominated for this training programme.");
    }
}

