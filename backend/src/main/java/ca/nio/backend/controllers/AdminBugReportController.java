package ca.nio.backend.controllers;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.BugReport;
import ca.nio.backend.model.BugReportStatus;
import ca.nio.backend.repositories.BugReportRepository;
import ca.nio.backend.services.BugReportService;
import ca.nio.backend.services.ErrorCode;
import ca.nio.backend.services.MediaService;
import ca.nio.backend.services.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Triage of the problems people report. Reports are never deleted; dismissing one is the way to set it aside.
 */
@RestController
@RequestMapping("/api/admin/bug-reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBugReportController {

    private final BugReportRepository reports;
    private final MediaService mediaService;
    private final BugReportService bugReportService;

    /** Every report in every status, newest first; there are few enough to filter in the browser. */
    @GetMapping
    public List<AdminBugReport> reports() {
        return reports.findAll().stream()
                .sorted(Comparator.comparing(BugReport::getCreatedAt).reversed())
                .map(AdminBugReport::of)
                .toList();
    }

    @GetMapping("/{id}")
    public AdminBugReportDetail report(@PathVariable UUID id) {
        return detail(find(id));
    }

    /** Sets the status and the internal note; a blank note clears it. */
    @PutMapping("/{id}")
    public AdminBugReportDetail triage(@PathVariable UUID id, @Valid @RequestBody BugReportTriageRequest request) {
        String note = request.adminNote() == null || request.adminNote().isBlank() ? null : request.adminNote().strip();
        return detail(bugReportService.triage(id, request.status(), note));
    }

    private BugReport find(UUID id) {
        return reports.findById(id).orElseThrow(() -> new NotFoundException(ErrorCode.BUG_REPORT_NOT_FOUND, "Bug report " + id + " not found"));
    }

    private AdminBugReportDetail detail(BugReport report) {
        return AdminBugReportDetail.of(report, mediaService.url(report.getScreenshotId()));
    }

    private static List<String> roles(BugReport report) {
        return Arrays.stream(report.getReporterRoles().split(",")).filter(role -> !role.isEmpty()).toList();
    }

    public record AdminBugReport(UUID id, String title, BugReportStatus status, String reporterName,
                                 List<String> reporterRoles, String pagePath, boolean hasScreenshot, Instant createdAt) {

        static AdminBugReport of(BugReport report) {
            return new AdminBugReport(report.getId(), report.getTitle(), report.getStatus(), report.getReporterName(),
                    roles(report), report.getPagePath(), report.getScreenshotId() != null, report.getCreatedAt());
        }

    }

    public record AdminBugReportDetail(UUID id, String title, String description, BugReportStatus status,
                                       UUID reporterId, String reporterUsername, String reporterName,
                                       @Nullable String reporterEmail, List<String> reporterRoles, String pagePath,
                                       @Nullable String userAgent, @Nullable String locale, @Nullable String viewport,
                                       @Nullable String appVersion, @Nullable URI screenshotUrl,
                                       @Nullable String adminNote, Instant createdAt, @Nullable Instant updatedAt) {

        static AdminBugReportDetail of(BugReport report, @Nullable URI screenshotUrl) {
            return new AdminBugReportDetail(report.getId(), report.getTitle(), report.getDescription(),
                    report.getStatus(), report.getReporterId(), report.getReporterUsername(), report.getReporterName(),
                    report.getReporterEmail(), roles(report), report.getPagePath(), report.getUserAgent(),
                    report.getLocale(), report.getViewport(), report.getAppVersion(), screenshotUrl,
                    report.getAdminNote(), report.getCreatedAt(), report.getUpdatedAt());
        }

    }

    public record BugReportTriageRequest(@NotNull BugReportStatus status,
                                         @Nullable @Size(max = 5000) String adminNote) {
    }

}
