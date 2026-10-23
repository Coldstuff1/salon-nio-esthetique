package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A problem someone reported from the site. Who sent it is copied from their token at the time, so the report
 * reads the same after their account changes.
 */
@Getter
@Setter
@NoArgsConstructor
public class BugReport {

    @Id
    private UUID id;
    private UUID reporterId;
    private String reporterUsername;
    private String reporterName;
    private @Nullable String reporterEmail;
    /** Comma-separated app roles, e.g. {@code client} or {@code employee,admin}. */
    private String reporterRoles;
    private String title;
    private String description;
    private String pagePath;
    private @Nullable String userAgent;
    private @Nullable String locale;
    private @Nullable String viewport;
    private @Nullable String appVersion;
    private @Nullable UUID screenshotId;
    private BugReportStatus status;
    private @Nullable String adminNote;
    private Instant createdAt;
    private @Nullable Instant updatedAt;
    @Version
    private Integer version;

    public BugReport(UUID reporterId, String reporterUsername, String reporterName, @Nullable String reporterEmail,
                     String reporterRoles, String title, String description, String pagePath) {
        this.id = UUID.randomUUID();
        this.reporterId = reporterId;
        this.reporterUsername = reporterUsername;
        this.reporterName = reporterName;
        this.reporterEmail = reporterEmail;
        this.reporterRoles = reporterRoles;
        this.title = title;
        this.description = description;
        this.pagePath = pagePath;
        this.status = BugReportStatus.NEW;
        this.createdAt = Instant.now();
    }

    /** Admins may move a report to any status, reopening it included. */
    public void triage(BugReportStatus status, @Nullable String adminNote) {
        this.status = status;
        this.adminNote = adminNote;
        this.updatedAt = Instant.now();
    }

}
