package ca.nio.backend.model;

/** Ways a notification leaves the site. The in-app list isn't one: every notification is in it. */
public enum NotificationChannel {
    EMAIL,
    /** Web Push, to every browser where the person turned it on. */
    PUSH
}
