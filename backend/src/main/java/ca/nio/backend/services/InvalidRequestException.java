package ca.nio.backend.services;

/**
 * Thrown when a request is well-formed but makes no sense for the domain.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

}
