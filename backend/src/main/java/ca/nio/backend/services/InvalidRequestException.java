package ca.nio.backend.services;

import java.util.Map;

/**
 * Thrown when a request is well-formed but makes no sense for the domain.
 */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public InvalidRequestException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

}
