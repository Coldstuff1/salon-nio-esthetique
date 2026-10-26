package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;

/**
 * The signed-in person's own notifications, whatever their role. The site polls the unread count for the bell.
 */
@RestController
@RequestMapping("/api/me/notifications")
@PreAuthorize("hasAnyRole('CLIENT', 'EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    /** The latest notifications, newest first. */
    @GetMapping
    public List<NotificationView> notifications(@AuthenticationPrincipal Jwt jwt) {
        return notifications.latest(CurrentUserService.userId(jwt)).stream()
                .map(n -> NotificationView.of(n, notifications.params(n)))
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(notifications.unreadCount(CurrentUserService.userId(jwt)));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        notifications.markRead(id, CurrentUserService.userId(jwt));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.markAllRead(CurrentUserService.userId(jwt));
    }

    /** @param params what the text is built from; which keys depends on the type */
    public record NotificationView(UUID id, NotificationType type, Map<String, String> params, @Nullable String link,
                                   Instant createdAt, @Nullable Instant readAt) {

        static NotificationView of(Notification notification, Map<String, String> params) {
            return new NotificationView(notification.getId(), notification.getType(), params, notification.getLink(),
                    notification.getCreatedAt(), notification.getReadAt());
        }

    }

    public record UnreadCount(long count) {
    }

}
