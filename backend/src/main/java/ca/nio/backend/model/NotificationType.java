package ca.nio.backend.model;

import java.util.Set;

/**
 * What a notification is about, from the recipient's side, and where it goes besides the in-app list. The site
 * renders the in-app text from this and the notification's params; emails are rendered by the backend.
 */
public enum NotificationType {
    /** To the client: their booking went through. Params: employeeName, startsAt. */
    BOOKING_CONFIRMED(NotificationChannel.EMAIL),
    /** To the client: the salon cancelled their booking. Params: employeeName, startsAt. */
    BOOKING_CANCELLED(NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the client, a day ahead: their appointment is coming up. Params: employeeName, startsAt. */
    BOOKING_REMINDER(NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the employee: a client booked with them. Params: customerName, startsAt. */
    STAFF_BOOKING_CREATED(NotificationChannel.PUSH),
    /** To the employee: someone else cancelled one of their bookings. Params: customerName, startsAt. */
    STAFF_BOOKING_CANCELLED(NotificationChannel.PUSH),
    /** To the employee, in the morning: their day's appointments. Params: count, startsAt (the first one). */
    STAFF_DAILY_AGENDA(NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the employee: an appointment ended a while ago and is still open. Params: customerName, startsAt. */
    STAFF_BOOKING_OPEN,
    /** To admins: a client cancelled shortly before the appointment. Params: customerName, employeeName, startsAt. */
    LATE_CANCELLATION,
    /** To admins: someone reported a problem. Params: title, reporterName. */
    BUG_REPORT_SUBMITTED(NotificationChannel.EMAIL, NotificationChannel.PUSH),
    /** To the reporter: their report was resolved or dismissed. Params: title, status. */
    BUG_REPORT_CLOSED(NotificationChannel.EMAIL);

    private final Set<NotificationChannel> channels;

    NotificationType(NotificationChannel... channels) {
        this.channels = Set.of(channels);
    }

    /** Channels used unless the person turned them off. */
    public Set<NotificationChannel> defaultChannels() {
        return channels;
    }

}
