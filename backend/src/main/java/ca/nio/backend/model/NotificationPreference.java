package ca.nio.backend.model;

/** Whether someone gets one type of notification over one channel, where they chose otherwise than the default. */
public record NotificationPreference(NotificationType type, NotificationChannel channel, boolean enabled) {
}
