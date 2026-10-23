package ca.nio.backend.services;

import java.util.Map;

/**
 * A request the domain refuses, with an {@link ErrorCode} for clients to translate and optional {@code params}
 * (e.g. the product that ran out) to fill into their message.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> params;

    protected DomainException(ErrorCode code, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.params = Map.copyOf(params);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }

}
