package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import ca.nio.backend.model.Notification;
import ca.nio.backend.repositories.NotificationRepository;
import ca.nio.backend.services.NotificationMessages;
import ca.nio.backend.services.NotificationService;
import ca.nio.backend.services.ReminderJobs;

/**
 * The clock-driven notifications, run by hand at chosen moments in 2031 so other tests' bookings (around today)
 * never fall in their windows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReminderJobsIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Montreal");
    static final LocalDate DAY = LocalDate.of(2031, 3, 11);

    @Autowired
    ReminderJobs jobs;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    NotificationService notificationService;
    @Autowired
    NotificationMessages messages;

    UUID employeeId = UUID.randomUUID();
    UUID otherEmployeeId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUpPeople() {
        for (UUID id : List.of(employeeId, otherEmployeeId)) {
            jdbc.sql("INSERT INTO employee (id, display_name) VALUES (?, 'Julie Roy')").param(id).update();
        }
        jdbc.sql("INSERT INTO customer (id, display_name, email) VALUES (?, 'Marie Tremblay', ?)")
                .params(customerId, "marie-" + customerId + "@example.com").update();
    }

    @Test
    void remindsClientsADayAheadOnce() {
        Instant now = at(DAY, 10, 0);
        UUID upcoming = booking(employeeId, now.plus(Duration.ofHours(20)), "RESERVED", now.minus(Duration.ofDays(3)));
        // booked within the reminder window: the confirmation is reminder enough
        booking(employeeId, now.plus(Duration.ofHours(22)), "RESERVED", now.minus(Duration.ofHours(1)));
        booking(employeeId, now.plus(Duration.ofHours(30)), "RESERVED", now.minus(Duration.ofDays(3)));
        booking(otherEmployeeId, now.plus(Duration.ofHours(21)), "CANCELLED", now.minus(Duration.ofDays(3)));

        jobs.remindClients(now);
        jobs.remindClients(now.plus(Duration.ofMinutes(5)));

        List<Notification> sent = notifications.latest(customerId, 10);
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getType().name()).isEqualTo("BOOKING_REMINDER");
            assertThat(n.getLink()).isEqualTo("/account/bookings/" + upcoming);
            assertThat(notificationService.params(n)).containsEntry("employeeName", "Julie Roy")
                    .containsEntry("startsAt", now.plus(Duration.ofHours(20)).toString());
        });
        assertThat(channels(sent.getFirst())).containsExactlyInAnyOrder("EMAIL", "PUSH");
    }

    @Test
    void sendsEachEmployeeTheirAgendaFromSevenInTheMorning() {
        booking(employeeId, at(DAY, 13, 0), "RESERVED", at(DAY, 0, 0).minus(Duration.ofDays(2)));
        booking(employeeId, at(DAY, 9, 30), "RESERVED", at(DAY, 0, 0).minus(Duration.ofDays(2)));
        booking(employeeId, at(DAY, 11, 0), "CANCELLED", at(DAY, 0, 0).minus(Duration.ofDays(2)));
        // tomorrow's appointment isn't part of today's agenda
        booking(otherEmployeeId, at(DAY.plusDays(1), 9, 0), "RESERVED", at(DAY, 0, 0).minus(Duration.ofDays(2)));

        jobs.sendAgendas(at(DAY, 6, 55));
        assertThat(notifications.latest(employeeId, 10)).isEmpty();

        jobs.sendAgendas(at(DAY, 7, 0));
        jobs.sendAgendas(at(DAY, 7, 5));

        List<Notification> sent = notifications.latest(employeeId, 10);
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getType().name()).isEqualTo("STAFF_DAILY_AGENDA");
            assertThat(n.getLink()).isEqualTo("/staff?day=" + DAY);
            assertThat(notificationService.params(n)).containsEntry("count", "2")
                    .containsEntry("startsAt", at(DAY, 9, 30).toString());
        });
        assertThat(channels(sent.getFirst())).containsExactlyInAnyOrder("EMAIL", "PUSH");
        assertThat(messages.text(sent.getFirst(), Locale.CANADA_FRENCH).body())
                .isEqualTo("Rendez-vous aujourd’hui : 2. Le premier est à 09 h 30.");
        assertThat(notifications.latest(otherEmployeeId, 10)).isEmpty();
    }

    @Test
    void sendsNoAgendaOnceTheCatchUpWindowHasPassed() {
        booking(employeeId, at(DAY, 15, 0), "RESERVED", at(DAY, 0, 0).minus(Duration.ofDays(2)));

        jobs.sendAgendas(at(DAY, 9, 0));

        assertThat(notifications.latest(employeeId, 10)).isEmpty();
    }

    @Test
    void asksEmployeesToCloseAppointmentsLeftOpen() {
        Instant now = at(DAY, 18, 0);
        UUID open = booking(employeeId, now.minus(Duration.ofHours(4)), "RESERVED", now.minus(Duration.ofDays(5)));
        // ended an hour ago (bookings here last an hour): still time to close it
        booking(employeeId, now.minus(Duration.ofHours(2)), "RESERVED", now.minus(Duration.ofDays(5)));
        booking(employeeId, now.minus(Duration.ofHours(6)), "COMPLETED", now.minus(Duration.ofDays(5)));
        // long forgotten
        booking(employeeId, now.minus(Duration.ofDays(5)), "RESERVED", now.minus(Duration.ofDays(10)));

        jobs.nudgeOpenBookings(now);
        jobs.nudgeOpenBookings(now.plus(Duration.ofMinutes(5)));

        List<Notification> sent = notifications.latest(employeeId, 10);
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getType().name()).isEqualTo("STAFF_BOOKING_OPEN");
            assertThat(n.getLink()).isEqualTo("/staff?day=" + DAY);
            assertThat(notificationService.params(n)).containsEntry("customerName", "Marie Tremblay");
        });
        // the bell only
        assertThat(channels(sent.getFirst())).isEmpty();
        assertThat(jdbc.sql("SELECT status FROM booking WHERE id = ?").param(open).query(String.class).single())
                .isEqualTo("RESERVED");
    }

    /** A one-hour booking. */
    private UUID booking(UUID employee, Instant startsAt, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO booking (id, customer_id, employee_id, starts_at, ends_at, status, subtotal_cents,
                                             notes, created_at)
                        VALUES (:id, :customer, :employee, :startsAt, :endsAt, :status, 0, '', :createdAt)""")
                .params(Map.of("id", id, "customer", customerId, "employee", employee,
                        "startsAt", Timestamp.from(startsAt),
                        "endsAt", Timestamp.from(startsAt.plus(Duration.ofHours(1))),
                        "status", status, "createdAt", Timestamp.from(createdAt)))
                .update();
        return id;
    }

    private List<String> channels(Notification notification) {
        return jdbc.sql("SELECT channel FROM notification_delivery WHERE notification_id = ?")
                .param(notification.getId()).query(String.class).list();
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toInstant();
    }

}
