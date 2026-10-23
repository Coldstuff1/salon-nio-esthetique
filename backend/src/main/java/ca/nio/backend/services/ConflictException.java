package ca.nio.backend.services;

import java.util.Map;

/**
 * Thrown when a request clashes with current state: slot taken, out of stock, wrong booking status.
 */
public class ConflictException extends DomainException {

    public ConflictException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

}
