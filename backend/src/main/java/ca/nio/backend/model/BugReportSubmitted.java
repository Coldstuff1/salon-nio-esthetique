package ca.nio.backend.model;

import java.util.UUID;

public record BugReportSubmitted(UUID reportId, UUID reporterId) {
}
