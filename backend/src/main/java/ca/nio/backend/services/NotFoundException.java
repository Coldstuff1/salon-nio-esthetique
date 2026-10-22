package ca.nio.backend.services;

/**
 * Thrown when a requested resource doesn't exist (or isn't visible to the caller).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

}
