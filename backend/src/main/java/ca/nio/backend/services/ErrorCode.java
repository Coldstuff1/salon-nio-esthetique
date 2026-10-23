package ca.nio.backend.services;

import java.util.Locale;

/**
 * Machine-readable reason for a failed request, sent as the problem's {@code code} property (e.g.
 * {@code slot-taken}). Clients translate it; the English {@code detail} is for logs and developers.
 */
public enum ErrorCode {

    // request shape
    VALIDATION,
    INVALID_REQUEST,
    INVALID_RANGE,

    // quoting and booking
    NO_SERVICE_SELECTED,
    DUPLICATE_SERVICE,
    DUPLICATE_PRODUCT,
    INVALID_QUANTITY,
    SERVICE_NOT_OFFERED,
    SERVICE_UNAVAILABLE,
    PRODUCT_NOT_OFFERED,
    PRODUCT_UNAVAILABLE,
    OUT_OF_STOCK,
    START_IN_PAST,
    OUTSIDE_WORKING_HOURS,
    SLOT_TAKEN,
    CANCEL_AFTER_START,
    BOOKING_CLOSED,
    INVALID_OUTCOME,

    // media
    FILE_SIZE,
    FILE_TYPE,
    FILE_NOT_UPLOADED,
    FILE_MISMATCH,

    // admin
    EMPLOYEE_EXISTS,

    // missing things
    BOOKING_NOT_FOUND,
    EMPLOYEE_NOT_FOUND,
    SERVICE_NOT_FOUND,
    PRODUCT_NOT_FOUND,
    TIME_OFF_NOT_FOUND,
    MEDIA_NOT_FOUND,

    // concurrency and storage
    DATA_CONFLICT,
    CONCURRENT_UPDATE;

    /** The wire form: {@code SLOT_TAKEN} → {@code slot-taken}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

}
