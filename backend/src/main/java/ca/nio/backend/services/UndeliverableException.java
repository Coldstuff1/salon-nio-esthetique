package ca.nio.backend.services;

/** A notification can't go out over a channel at all (e.g. no email address), so retrying is pointless. */
public class UndeliverableException extends RuntimeException {

    public UndeliverableException(String message) {
        super(message);
    }

}
