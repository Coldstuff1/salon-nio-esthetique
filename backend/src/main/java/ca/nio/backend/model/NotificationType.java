package ca.nio.backend.model;

import java.util.Collection;
import java.util.Set;

/**
 * What a notification is about, from the recipient's side, who gets it, and where it goes besides the in-app list
 * unless they chose otherwise. The site renders the in-app text from this and the notification's params; emails
 * and pushes are rendered by the backend.
 */
public enum NotificationType {
    /** To the client: their booking went through. Params: employeeName, startsAt. */
    BOOKING_CONFIRMED("client", NotificationChannel.EMAIL),
    /** To the client: the salon cancelled their booking. Params: employeeName, startsAt. */
    BOOKING_CANCELLED("client", NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the client, a day ahead: their appointment is coming up. Params: employeeName, startsAt. */
    BOOKING_REMINDER("client", NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the employee: a client booked with them. Params: customerName, startsAt. */
    STAFF_BOOKING_CREATED("employee", NotificationChannel.PUSH),
    /** To the employee: someone else cancelled one of their bookings. Params: customerName, startsAt. */
    STAFF_BOOKING_CANCELLED("employee", NotificationChannel.PUSH),
    /** To the employee, in the morning: their day's appointments. Params: count, startsAt (the first one). */
    STAFF_DAILY_AGENDA("employee", NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the employee: an appointment ended a while ago and is still open. Params: customerName, startsAt. */
    STAFF_BOOKING_OPEN("employee"),
    /** To admins: a client cancelled shortly before the appointment. Params: customerName, employeeName, startsAt. */
    LATE_CANCELLATION("admin"),
    /** To admins: someone reported a problem. Params: title, reporterName. */
    BUG_REPORT_SUBMITTED("admin", NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the reporter, whatever their role: their report was resolved or dismissed. Params: title, status. */
    BUG_REPORT_CLOSED(null, NotificationChannel.EMAIL);

    private final String role;
    private final Set<NotificationChannel> channels;

    /** @param role the app role it's sent to ({@code client}, {@code employee}, {@code admin}); null for anyone */
    NotificationType(String role, NotificationChannel... channels) {
        this.role = role;
        this.channels = Set.of(channels);
    }

    /** Channels used unless the person turned them off. */
    public Set<NotificationChannel> defaultChannels() {
        return channels;
    }

    /** Whether someone with these app roles can get it, so it belongs in their settings. */
    public boolean concerns(Collection<String> roles) {
        return role == null || roles.contains(role);
    }

}
