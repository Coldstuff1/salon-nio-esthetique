package ca.nio.backend.controllers;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ca.nio.backend.services.ConflictException;
import ca.nio.backend.services.DomainException;
import ca.nio.backend.services.ErrorCode;
import ca.nio.backend.services.ForbiddenException;
import ca.nio.backend.services.InvalidRequestException;
import ca.nio.backend.services.NotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Every error is a {@link ProblemDetail} with a {@code code} property (an {@link ErrorCode} such as
 * {@code slot-taken}) for clients to translate, plus {@code params} to fill into the message and, for bean
 * validation failures, the offending {@code fields}.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";
    static final String PARAMS = "params";
    static final String FIELDS = "fields";

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ProblemDetail forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail invalid(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    /**
     * Broken invariants in model constructors, e.g. a time window that ends before it starts. Libraries throw
     * this too, so the message is only logged: it can describe internals.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail badRequest(IllegalArgumentException e) {
        log.debug("Rejected request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ErrorCode.INVALID_REQUEST, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        // the overlap constraint is the last guard against two reservations racing for one slot
        if (message.contains("booking_no_overlap")) {
            return problem(HttpStatus.CONFLICT, "That time slot is already booked", ErrorCode.SLOT_TAKEN, Map.of());
        }
        if (message.contains("product_sku_key")) {
            return problem(HttpStatus.CONFLICT, "Another product already uses that SKU", ErrorCode.SKU_TAKEN,
                    Map.of());
        }
        return problem(HttpStatus.CONFLICT, "Request conflicts with existing data", ErrorCode.DATA_CONFLICT,
                Map.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Data was changed by someone else; retry", ErrorCode.CONCURRENT_UPDATE,
                Map.of());
    }

    /** Spring's own 400s (bean validation, unreadable JSON, malformed ids) get a code too. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                                      HttpHeaders headers, HttpStatusCode statusCode,
                                                                      WebRequest request) {
        // Spring often passes no body and builds the ProblemDetail in here, so add the code afterwards
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && statusCode.value() == HttpStatus.BAD_REQUEST.value()
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            switch (ex) {
                case MethodArgumentNotValidException invalid -> {
                    problem.setProperty(CODE, ErrorCode.VALIDATION.value());
                    problem.setProperty(FIELDS, invalid.getBindingResult().getFieldErrors().stream()
                            .map(FieldError::getField).distinct().toList());
                }
                case HandlerMethodValidationException invalid -> {
                    problem.setProperty(CODE, ErrorCode.VALIDATION.value());
                    problem.setProperty(FIELDS, fields(invalid));
                }
                default -> problem.setProperty(CODE, ErrorCode.INVALID_REQUEST.value());
            }
        }
        return response;
    }

    private static List<String> fields(HandlerMethodValidationException e) {
        return e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .filter(FieldError.class::isInstance)
                .map(error -> ((FieldError) error).getField())
                .distinct()
                .toList();
    }

    private static ProblemDetail problem(HttpStatus status, DomainException e) {
        return problem(status, e.getMessage(), e.code(), e.params());
    }

    private static ProblemDetail problem(HttpStatus status, @Nullable String detail, ErrorCode code,
                                         Map<String, Object> params) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code.value());
        if (!params.isEmpty()) {
            problem.setProperty(PARAMS, params);
        }
        return problem;
    }

}
