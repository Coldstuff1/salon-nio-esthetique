package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.BugReport;
import ca.nio.backend.services.BugReportService;
import ca.nio.backend.services.CurrentUserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Anyone signed in can report a problem; admins triage them through {@link AdminBugReportController}. An unverified
 * email is no obstacle, since that may be the very problem being reported.
 */
@RestController
@RequestMapping("/api/bug-reports")
@PreAuthorize("hasAnyRole('CLIENT', 'EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class BugReportController {

    private static final int MAX_USER_AGENT = 512;

    private final BugReportService bugReports;

    /** The reporter comes from the token and the browser from the {@code User-Agent} header. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BugReportCreated submit(@AuthenticationPrincipal Jwt jwt,
                                   @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                   @Nullable String userAgent,
                                   @Valid @RequestBody BugReportRequest request) {
        BugReport report = new BugReport(CurrentUserService.userId(jwt), jwt.getClaimAsString("preferred_username"),
                CurrentUserService.displayName(jwt), jwt.getClaimAsString("email"),
                String.join(",", CurrentUserService.appRoles(jwt)), request.title().strip(),
                request.description().strip(), request.pagePath());
        report.setUserAgent(userAgent == null || userAgent.isBlank() ? null
                : userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT)));
        report.setLocale(request.locale());
        report.setViewport(request.viewport());
        report.setAppVersion(request.appVersion());
        report.setScreenshotId(request.screenshotId());
        BugReport saved = bugReports.submit(report);
        return new BugReportCreated(saved.getId(), saved.getCreatedAt());
    }

    /**
     * @param pagePath     the route the reporter was on, query and hash included
     * @param screenshotId an image uploaded through {@code POST /api/media/uploads}
     */
    public record BugReportRequest(@NotBlank @Size(max = 120) String title,
                                   @NotBlank @Size(max = 5000) String description,
                                   @NotBlank @Size(max = 2048) String pagePath,
                                   @Nullable @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?") String locale,
                                   @Nullable @Pattern(regexp = "\\d{1,5}x\\d{1,5}") String viewport,
                                   @Nullable @Size(max = 40) String appVersion,
                                   @Nullable UUID screenshotId) {
    }

    public record BugReportCreated(UUID id, Instant createdAt) {
    }

}
