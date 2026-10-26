package ca.nio.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingStatus;
import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.repositories.BookingRepository;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notifications that come from the clock rather than from something someone did: appointment reminders, the
 * employees' morning agenda, and a nudge to close appointments left open. Each pass looks at a window of time
 * instead of an exact moment, so a pass that's late or skipped (a restart, a deploy) is made up by the next one;
 * dedupe keys keep anyone from being notified twice, however many passes see the same booking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJobs {

    /** The agenda still goes out this long after its time, in case the backend was down at that moment. */
    static final Duration AGENDA_CATCH_UP = Duration.ofHours(2);
    /** Open appointments older than this are left alone: someone has noticed by then, or never will. */
    static final Duration OPEN_BOOKING_LOOKBACK = Duration.ofDays(3);

    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final NotificationService notifications;
    private final NotificationProperties properties;
    private final SalonProperties salon;

    @Scheduled(cron = "${salon.notifications.jobs.cron:0 */5 * * * *}", zone = "${salon.zone:America/Toronto}")
    public void run() {
        Instant now = Instant.now();
        // one failing job mustn't keep the others from running
        for (Runnable job : List.<Runnable>of(() -> remindClients(now), () -> sendAgendas(now),
                () -> nudgeOpenBookings(now))) {
            try {
                job.run();
            } catch (RuntimeException e) {
                log.error("Scheduled notifications failed", e);
            }
        }
    }

    /** Reminds clients of appointments starting within the reminder lead, unless they booked within it. */
    public void remindClients(Instant now) {
        Duration lead = properties.jobs().reminder();
        for (Booking booking : bookings.findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                BookingStatus.RESERVED, now, now.plus(lead))) {
            if (booking.getCreatedAt().isAfter(booking.getStartsAt().minus(lead))) {
                continue;
            }
            notifications.notify(booking.getCustomerId(), NotificationType.BOOKING_REMINDER,
                    Map.of("employeeName", employeeName(booking.getEmployeeId()),
                            "startsAt", booking.getStartsAt().toString()),
                    "/account/bookings/" + booking.getId(),
                    key(NotificationType.BOOKING_REMINDER, booking.getId(), booking.getCustomerId()));
        }
    }

    /**
     * From the agenda time on (and for a while after), tells each employee with appointments today how many
     * they have and when the first one is. Employees with none hear nothing.
     */
    public void sendAgendas(Instant now) {
        ZonedDateTime local = now.atZone(salon.zone());
        LocalDate today = local.toLocalDate();
        ZonedDateTime agendaAt = today.atTime(properties.jobs().agendaTime()).atZone(salon.zone());
        if (local.isBefore(agendaAt) || !local.isBefore(agendaAt.plus(AGENDA_CATCH_UP))) {
            return;
        }
        Map<UUID, List<Booking>> byEmployee = bookings.findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                        BookingStatus.RESERVED, today.atStartOfDay(salon.zone()).toInstant(),
                        today.plusDays(1).atStartOfDay(salon.zone()).toInstant())
                .stream().collect(Collectors.groupingBy(Booking::getEmployeeId));
        byEmployee.forEach((employeeId, day) -> {
            Instant first = day.stream().map(Booking::getStartsAt).min(Comparator.naturalOrder()).orElseThrow();
            notifications.notify(employeeId, NotificationType.STAFF_DAILY_AGENDA,
                    Map.of("count", Integer.toString(day.size()), "startsAt", first.toString()),
                    "/staff?day=" + today,
                    NotificationType.STAFF_DAILY_AGENDA + ":" + today + ":" + employeeId);
        });
    }

    /** Asks employees to mark appointments that ended a while ago as completed or no-show. */
    public void nudgeOpenBookings(Instant now) {
        Instant endedBefore = now.minus(properties.jobs().openBooking());
        for (Booking booking : bookings.findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(
                BookingStatus.RESERVED, endedBefore.minus(OPEN_BOOKING_LOOKBACK), endedBefore)) {
            notifications.notify(booking.getEmployeeId(), NotificationType.STAFF_BOOKING_OPEN,
                    Map.of("customerName", customerName(booking.getCustomerId()),
                            "startsAt", booking.getStartsAt().toString()),
                    "/staff?day=" + booking.getStartsAt().atZone(salon.zone()).toLocalDate(),
                    key(NotificationType.STAFF_BOOKING_OPEN, booking.getId(), booking.getEmployeeId()));
        }
    }

    private String employeeName(UUID employeeId) {
        return employees.findById(employeeId).map(Employee::getDisplayName).orElse("");
    }

    private String customerName(UUID customerId) {
        return customers.findById(customerId).map(Customer::getDisplayName).orElse("");
    }

    private static String key(NotificationType type, UUID subjectId, UUID recipientId) {
        return type + ":" + subjectId + ":" + recipientId;
    }

}
