package ca.nio.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.BugReport;
import ca.nio.backend.repositories.BugReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BugReportService {

    private final BugReportRepository reports;
    private final MediaService media;
    private final SalonProperties salon;

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
        return reports.save(report);
    }

}
