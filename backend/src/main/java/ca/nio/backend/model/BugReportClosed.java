package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

/** An admin resolved or dismissed a report that was still open. */
public record BugReportClosed(UUID reportId, UUID reporterId, BugReportStatus status, Instant closedAt) {
}
