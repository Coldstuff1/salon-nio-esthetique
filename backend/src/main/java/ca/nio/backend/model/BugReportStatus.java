package ca.nio.backend.model;

public enum BugReportStatus {
    /** Sent and not looked at yet. */
    NEW,
    /** An admin is on it. */
    IN_PROGRESS,
    /** Fixed. */
    RESOLVED,
    /** Not a bug, a duplicate, or won't be fixed. */
    DISMISSED
}
