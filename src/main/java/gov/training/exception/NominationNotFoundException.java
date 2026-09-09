package gov.training.exception;
public class NominationNotFoundException extends RuntimeException {
    public NominationNotFoundException(long id) {
        super("Nomination " + id + " was not found.");
    }
}

