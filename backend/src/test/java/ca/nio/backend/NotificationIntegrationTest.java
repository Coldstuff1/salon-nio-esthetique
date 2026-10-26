package ca.nio.backend;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import ca.nio.backend.model.BookingCancelled;
import ca.nio.backend.model.LocalizedText;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * Bookings and bug reports turn into notifications for the people they concern, and each person reads and
 * clears only their own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ServiceTypeRepository serviceTypes;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    TransactionTemplate transactions;

    UUID employeeId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    ServiceType facial;
    LocalDate monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() throws Exception {
        facial = serviceTypes.save(new ServiceType(new LocalizedText("Facial", "Soin du visage"), null, null));
        send(put("/api/staff/me/availability"), employee(), List.of(
                Map.of("dayOfWeek", 1, "startTime", "09:00", "endTime", "12:00")))
                .andExpect(status().isOk());
        send(put("/api/staff/me/service-offerings/" + facial.getId()), employee(),
                Map.of("priceCents", 8000, "durationMinutes", 60))
                .andExpect(status().isOk());
        // the site calls /api/me on load, which is how the backend learns who the admins are
        mockMvc.perform(get("/api/me").with(admin())).andExpect(status().isOk());
    }

    @Test
    void bookingNotifiesTheClientAndTheEmployee() throws Exception {
        String bookingId = book();

        awaitCount(customer(), 1);
        mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$[0].params.employeeName").value("Julie Roy"))
                .andExpect(jsonPath("$[0].params.startsAt").value(at(9).toString()))
                .andExpect(jsonPath("$[0].link").value("/account/bookings/" + bookingId))
                .andExpect(jsonPath("$[0].readAt").doesNotExist());

        awaitCount(employee(), 1);
        mockMvc.perform(get("/api/me/notifications").with(employee()))
                .andExpect(jsonPath("$[0].type").value("STAFF_BOOKING_CREATED"))
                .andExpect(jsonPath("$[0].params.customerName").value("Marie Tremblay"))
                .andExpect(jsonPath("$[0].link").value("/staff?day=" + monday));
    }

    @Test
    void readingClearsTheUnreadCount() throws Exception {
        book();
        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications/unread-count").with(customer()))
                .andExpect(jsonPath("$.count").value(1)));
        String id = JsonPath.read(mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        // someone else's notification is as good as missing
        mockMvc.perform(post("/api/me/notifications/" + id + "/read").with(employee()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("notification-not-found"));

        mockMvc.perform(post("/api/me/notifications/" + id + "/read").with(customer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/me/notifications/" + id + "/read").with(customer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications/unread-count").with(customer()))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$[0].readAt").isNotEmpty());

        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications/unread-count").with(employee()))
                .andExpect(jsonPath("$.count").value(1)));
        mockMvc.perform(post("/api/me/notifications/read-all").with(employee()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/notifications/unread-count").with(employee()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void cancellingNotifiesWhoeverDidntCancel() throws Exception {
        String bookingId = book();
        awaitCount(employee(), 1);
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").with(employee()))
                .andExpect(status().isOk());

        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$[?(@.type == 'BOOKING_CANCELLED')].params.employeeName").value("Julie Roy")));
        // the employee only has the booking itself: they cancelled, so they aren't told (the client's notification
        // above is written in the same transaction their own would have been)
        mockMvc.perform(get("/api/me/notifications").with(employee()))
                .andExpect(jsonPath("$[*].type").value(Matchers.contains("STAFF_BOOKING_CREATED")));
    }

    @Test
    void clientCancellingFarAheadOnlyNotifiesTheEmployee() throws Exception {
        String bookingId = book();
        awaitCount(customer(), 1);
        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel").with(customer()))
                .andExpect(status().isOk());

        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(employee()))
                .andExpect(jsonPath("$[?(@.type == 'STAFF_BOOKING_CANCELLED')].params.customerName")
                        .value("Marie Tremblay")));
        mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$[*].type").value(Matchers.contains("BOOKING_CONFIRMED")));
        mockMvc.perform(get("/api/me/notifications").with(admin()))
                .andExpect(jsonPath("$[?(@.type == 'LATE_CANCELLATION')]").isEmpty());
    }

    /** Published by hand: a booking hours away would depend on the time of day the test runs. */
    @Test
    void lateCancellationByTheClientNotifiesAdminsOnce() {
        UUID bookingId = UUID.randomUUID();
        BookingCancelled cancelled = new BookingCancelled(bookingId, customerId, employeeId,
                Instant.now().plus(Duration.ofHours(3)), customerId);
        transactions.executeWithoutResult(tx -> events.publishEvent(cancelled));
        // delivered again, e.g. replayed after a restart
        transactions.executeWithoutResult(tx -> events.publishEvent(cancelled));

        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(admin()))
                .andExpect(jsonPath("$[?(@.type == 'LATE_CANCELLATION')].link")
                        .value(Matchers.everyItem(Matchers.endsWith("&employee=" + employeeId))))
                .andExpect(jsonPath("$[?(@.type == 'LATE_CANCELLATION')]", Matchers.hasSize(1))));
    }

    @Test
    void bugReportsNotifyAdminsAndTheReporterWhenClosed() throws Exception {
        String reportId = JsonPath.read(send(post("/api/bug-reports"), customer(), Map.of("title", "Rien ne marche",
                "description", "Quand je clique, rien ne se passe.", "pagePath", "/book"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(admin()))
                .andExpect(jsonPath("$[?(@.type == 'BUG_REPORT_SUBMITTED')].link")
                        .value(Matchers.contains("/admin/bug-reports/" + reportId))));

        send(put("/api/admin/bug-reports/" + reportId), admin(), Map.of("status", "IN_PROGRESS"))
                .andExpect(status().isOk());
        send(put("/api/admin/bug-reports/" + reportId), admin(), Map.of("status", "RESOLVED"))
                .andExpect(status().isOk());
        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("BUG_REPORT_CLOSED"))
                .andExpect(jsonPath("$[0].params.title").value("Rien ne marche"))
                .andExpect(jsonPath("$[0].params.status").value("RESOLVED")));

        // a note on a closed report isn't news; reopening and closing it again is
        send(put("/api/admin/bug-reports/" + reportId), admin(), Map.of("status", "RESOLVED", "adminNote", "Corrigé"))
                .andExpect(status().isOk());
        send(put("/api/admin/bug-reports/" + reportId), admin(), Map.of("status", "NEW"))
                .andExpect(status().isOk());
        send(put("/api/admin/bug-reports/" + reportId), admin(), Map.of("status", "DISMISSED"))
                .andExpect(status().isOk());
        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(customer()))
                .andExpect(jsonPath("$[*].params.status").value(Matchers.contains("DISMISSED", "RESOLVED"))));
    }

    private void awaitCount(JwtRequestPostProcessor auth, int count) {
        await().untilAsserted(() -> mockMvc.perform(get("/api/me/notifications").with(auth))
                .andExpect(jsonPath("$.length()").value(count)));
    }

    private String book() throws Exception {
        return JsonPath.read(send(post("/api/bookings"), customer(), Map.of("employeeId", employeeId,
                "startsAt", at(9), "serviceIds", List.of(facial.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, Object body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(body)).with(auth));
    }

    private Instant at(int hour) {
        return ZonedDateTime.of(monday, LocalTime.of(hour, 0), ZONE).toInstant();
    }

    private JwtRequestPostProcessor customer() {
        return keycloakJwt(customerId, "client", "Marie Tremblay");
    }

    private JwtRequestPostProcessor employee() {
        return keycloakJwt(employeeId, "employee", "Julie Roy");
    }

    private JwtRequestPostProcessor admin() {
        return keycloakJwt(adminId, "admin", "Admin");
    }

    private static JwtRequestPostProcessor keycloakJwt(UUID subject, String role, String name) {
        return jwt()
                .jwt(token -> token
                        .subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("name", name)
                        .claim("email", role + "-" + subject + "@example.com")
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static String json(Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

}
