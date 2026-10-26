package ca.nio.backend.model;

public enum DeliveryStatus {
    /** Waiting for its first or next attempt. */
    PENDING,
    SENT,
    /** Nowhere to send it, e.g. no email address on file. */
    SKIPPED,
    /** Every attempt failed. */
    FAILED
}
