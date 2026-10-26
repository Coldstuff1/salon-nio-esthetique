package ca.nio.backend.controllers;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.PushSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Browsers where the signed-in person turned push notifications on. The site sends what
 * {@code PushManager.subscribe()} gave it, and takes it back on sign-out so the next person on that browser doesn't
 * get their notifications.
 */
@RestController
@RequestMapping("/api/me/push-subscriptions")
@PreAuthorize("hasAnyRole('CLIENT', 'EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService subscriptions;

    /** Adds this browser, or refreshes its keys. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                          @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) @Nullable String userAgent,
                          @AuthenticationPrincipal Jwt jwt) {
        subscriptions.subscribe(CurrentUserService.userId(jwt), request.endpoint(), request.keys().p256dh(),
                request.keys().auth(), userAgent);
    }

    /** Stops pushing to this browser; fine if it was never added. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@RequestParam @NotBlank @Size(max = 1024) String endpoint, @AuthenticationPrincipal Jwt jwt) {
        subscriptions.unsubscribe(CurrentUserService.userId(jwt), endpoint);
    }

    /** {@code PushSubscription.toJSON()} as the browser gives it. */
    public record PushSubscriptionRequest(@NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid Keys keys) {
    }

    public record Keys(@NotBlank @Size(max = 100) String p256dh, @NotBlank @Size(max = 50) String auth) {
    }

}
