package ca.nio.backend.services;

import java.util.Map;

/**
 * Thrown when a requested resource doesn't exist (or isn't visible to the caller).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public NotFoundException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

}
