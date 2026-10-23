package ca.nio.backend.services;

import java.util.Map;

/**
 * Thrown when the caller has the right role but their account may not do this yet (or any more): an unverified
 * email, a deactivated employee.
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ForbiddenException(ErrorCode code, String message, Map<String, Object> params) {
        super(code, message, params);
    }

}
