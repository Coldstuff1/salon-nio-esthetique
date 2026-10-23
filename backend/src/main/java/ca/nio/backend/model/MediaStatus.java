package ca.nio.backend.model;

public enum MediaStatus {
    /** Upload URL handed out; the object may not exist yet. */
    PENDING,
    /** Object verified in storage and attached to something. */
    READY
}
