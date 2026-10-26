package ca.nio.backend.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import ca.nio.backend.model.BookingCancelled;
import ca.nio.backend.model.BookingReserved;
import ca.nio.backend.model.BugReport;
import ca.nio.backend.model.BugReportClosed;
import ca.nio.backend.model.BugReportSubmitted;
import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.NotificationRecipient;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.repositories.BugReportRepository;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.NotificationRecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns domain events into notifications. Each listener runs after the event's transaction commits, on its own
 * thread and transaction; the event registry retries it after a crash, and dedupe keys make the retry harmless.
 */
@Component
@RequiredArgsConstructor
public class NotificationListeners {

    private final NotificationService notifications;
    private final NotificationRecipientRepository recipients;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final BugReportRepository bugReports;
    private final SalonProperties salon;

    @ApplicationModuleListener
    void on(BookingReserved event) {
        String startsAt = event.startsAt().toString();
        notifications.notify(event.customerId(), NotificationType.BOOKING_CONFIRMED,
                Map.of("employeeName", employeeName(event.employeeId()), "startsAt", startsAt),
                "/account/bookings/" + event.bookingId(),
                key(NotificationType.BOOKING_CONFIRMED, event.bookingId(), event.customerId()));
        notifications.notify(event.employeeId(), NotificationType.STAFF_BOOKING_CREATED,
                Map.of("customerName", customerName(event.customerId()), "startsAt", startsAt),
                "/staff?day=" + salonDay(event.startsAt()),
                key(NotificationType.STAFF_BOOKING_CREATED, event.bookingId(), event.employeeId()));
    }

    /**
     * Whoever didn't cancel hears about it: the client when the salon cancelled, the employee when someone else
     * did. Admins hear about clients cancelling at the last minute.
     */
    @ApplicationModuleListener
    void on(BookingCancelled event) {
        String startsAt = event.startsAt().toString();
        boolean byClient = event.cancelledBy().equals(event.customerId());
        if (!byClient) {
            notifications.notify(event.customerId(), NotificationType.BOOKING_CANCELLED,
                    Map.of("employeeName", employeeName(event.employeeId()), "startsAt", startsAt),
                    "/account/bookings/" + event.bookingId(),
                    key(NotificationType.BOOKING_CANCELLED, event.bookingId(), event.customerId()));
        }
        if (!event.cancelledBy().equals(event.employeeId())) {
            notifications.notify(event.employeeId(), NotificationType.STAFF_BOOKING_CANCELLED,
                    Map.of("customerName", customerName(event.customerId()), "startsAt", startsAt),
                    "/staff?day=" + salonDay(event.startsAt()),
                    key(NotificationType.STAFF_BOOKING_CANCELLED, event.bookingId(), event.employeeId()));
        }
        if (byClient && event.startsAt().isBefore(Instant.now().plus(salon.lateCancellationNotice()))) {
            Map<String, String> params = Map.of("customerName", customerName(event.customerId()),
                    "employeeName", employeeName(event.employeeId()), "startsAt", startsAt);
            String link = "/admin/bookings?week=" + salonDay(event.startsAt()) + "&employee=" + event.employeeId();
            for (NotificationRecipient admin : recipients.findByRole("admin")) {
                notifications.notify(admin.getId(), NotificationType.LATE_CANCELLATION, params, link,
                        key(NotificationType.LATE_CANCELLATION, event.bookingId(), admin.getId()));
            }
        }
    }

    @ApplicationModuleListener
    void on(BugReportSubmitted event) {
        BugReport report = bugReports.findById(event.reportId()).orElseThrow();
        Map<String, String> params = Map.of("title", report.getTitle(), "reporterName", report.getReporterName());
        for (NotificationRecipient admin : recipients.findByRole("admin")) {
            if (!admin.getId().equals(event.reporterId())) {
                notifications.notify(admin.getId(), NotificationType.BUG_REPORT_SUBMITTED, params,
                        "/admin/bug-reports/" + report.getId(),
                        key(NotificationType.BUG_REPORT_SUBMITTED, report.getId(), admin.getId()));
            }
        }
    }

    /** People have no page listing their reports, so there is nothing to link to. */
    @ApplicationModuleListener
    void on(BugReportClosed event) {
        BugReport report = bugReports.findById(event.reportId()).orElseThrow();
        // a report closed, reopened and closed again notifies each time
        notifications.notify(event.reporterId(), NotificationType.BUG_REPORT_CLOSED,
                Map.of("title", report.getTitle(), "status", event.status().name()), null,
                key(NotificationType.BUG_REPORT_CLOSED, report.getId(), event.reporterId()) + ":"
                        + event.closedAt().toEpochMilli());
    }

    private String employeeName(UUID employeeId) {
        return employees.findById(employeeId).map(Employee::getDisplayName).orElse("");
    }

    private String customerName(UUID customerId) {
        return customers.findById(customerId).map(Customer::getDisplayName).orElse("");
    }

    private LocalDate salonDay(Instant instant) {
        return instant.atZone(salon.zone()).toLocalDate();
    }

    private static String key(NotificationType type, UUID subjectId, UUID recipientId) {
        return type + ":" + subjectId + ":" + recipientId;
    }

}
