package ca.nio.backend.model;

/**
 * What a notification is about, from the recipient's side. The client renders the text from this and the
 * notification's params.
 */
public enum NotificationType {
    /** To the client: their booking went through. Params: employeeName, startsAt. */
    BOOKING_CONFIRMED,
    /** To the client: the salon cancelled their booking. Params: employeeName, startsAt. */
    BOOKING_CANCELLED,
    /** To the employee: a client booked with them. Params: customerName, startsAt. */
    STAFF_BOOKING_CREATED,
    /** To the employee: someone else cancelled one of their bookings. Params: customerName, startsAt. */
    STAFF_BOOKING_CANCELLED,
    /** To admins: a client cancelled shortly before the appointment. Params: customerName, employeeName, startsAt. */
    LATE_CANCELLATION,
    /** To admins: someone reported a problem. Params: title, reporterName. */
    BUG_REPORT_SUBMITTED,
    /** To the reporter: their report was resolved or dismissed. Params: title, status. */
    BUG_REPORT_CLOSED
}
