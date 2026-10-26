package ca.nio.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.BugReport;
import ca.nio.backend.model.BugReportClosed;
import ca.nio.backend.model.BugReportStatus;
import ca.nio.backend.model.BugReportSubmitted;
import ca.nio.backend.repositories.BugReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private final BugReportRepository reports;
    private final MediaService media;
    private final SalonProperties salon;
    private final ApplicationEventPublisher events;

    /**
     * Saves a new report once the reporter is under their daily cap, attaching the screenshot if there is one.
     * The cap is checked first so a refused report leaves its screenshot unclaimed, for the hourly purge.
     */
    @Transactional
    public BugReport submit(BugReport report) {
        long recent = reports.countByReporterIdAndCreatedAtAfter(report.getReporterId(),
                Instant.now().minus(Duration.ofDays(1)));
        if (recent >= salon.maxBugReportsPerDay()) {
            throw new ConflictException(ErrorCode.TOO_MANY_BUG_REPORTS, "Too many bug reports today; try again tomorrow",
                    Map.of("max", salon.maxBugReportsPerDay()));
        }
        if (report.getScreenshotId() != null) {
            media.claimImage(report.getScreenshotId(), report.getReporterId());
        }
        BugReport saved = reports.save(report);
        events.publishEvent(new BugReportSubmitted(saved.getId(), saved.getReporterId()));
        return saved;
    }

    /** Sets the status and the internal note; the reporter hears about it when an open report gets closed. */
    @Transactional
    public BugReport triage(UUID reportId, BugReportStatus status, @Nullable String adminNote) {
        BugReport report = reports.findById(reportId).orElseThrow(() ->
                new NotFoundException(ErrorCode.BUG_REPORT_NOT_FOUND, "Bug report " + reportId + " not found"));
        boolean closes = isOpen(report.getStatus()) && !isOpen(status);
        report.triage(status, adminNote);
        BugReport saved = reports.save(report);
        if (closes) {
            events.publishEvent(new BugReportClosed(saved.getId(), saved.getReporterId(), status, saved.getUpdatedAt()));
        }
        return saved;
    }

    private static boolean isOpen(BugReportStatus status) {
        return status == BugReportStatus.NEW || status == BugReportStatus.IN_PROGRESS;
    }

}
