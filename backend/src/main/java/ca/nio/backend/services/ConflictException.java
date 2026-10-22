package ca.nio.backend.services;

/**
 * Thrown when a request clashes with current state: slot taken, out of stock, wrong booking status.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

}
