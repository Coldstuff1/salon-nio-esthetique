package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;

import com.jayway.jsonpath.JsonPath;

import ca.nio.backend.model.LocalizedText;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;

/**
 * Notifications whose type calls for email reach Mailpit, in the recipient's language, with a link back to the
 * site; types that don't, and people without an address, send nothing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationEmailIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ServiceTypeRepository serviceTypes;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    @Qualifier("mailpitContainer")
    GenericContainer<?> mailpit;

    HttpClient http = HttpClient.newHttpClient();
    UUID employeeId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    String customerEmail = "marie-" + customerId + "@example.com";
    ServiceType facial;
    LocalDate monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach
    void setUpEmployee() throws Exception {
        facial = serviceTypes.save(new ServiceType(new LocalizedText("Facial", "Soin du visage"), null, null));
        send(put("/api/staff/me/availability"), employee(), List.of(
                Map.of("dayOfWeek", 1, "startTime", "09:00", "endTime", "12:00")))
                .andExpect(status().isOk());
        send(put("/api/staff/me/service-offerings/" + facial.getId()), employee(),
                Map.of("priceCents", 8000, "durationMinutes", 60))
                .andExpect(status().isOk());
    }

    /** No visit since signing up: the profile's email, and French, the salon's language. */
    @Test
    void emailsTheBookingConfirmationInFrenchByDefault() throws Exception {
        String bookingId = book();

        Map<String, Object> email = awaitEmail(customerEmail);
        assertThat(JsonPath.<String>read(email, "$.Subject")).isEqualTo("Rendez-vous confirmé");
        assertThat(JsonPath.<String>read(email, "$.From.Name")).isEqualTo("Salon Nio Esthétique");
        assertThat(JsonPath.<String>read(email, "$.Text"))
                .contains("Bonjour Marie Tremblay,")
                .contains("Votre rendez-vous avec Julie Roy le lundi " + monday.getDayOfMonth())
                .contains("http://localhost:5173/account/bookings/" + bookingId);
        assertThat(JsonPath.<String>read(email, "$.HTML"))
                .contains("href=\"http://localhost:5173/account/bookings/" + bookingId + "\"")
                .contains("Voir mon rendez-vous");
        assertThat(deliveryStatuses(customerId)).containsExactly("SENT");
        // the employee's notification has no email by default
        await().until(() -> !jdbc.sql("SELECT id FROM notification WHERE recipient_id = ?").param(employeeId)
                .query().listOfRows().isEmpty());
        assertThat(deliveryStatuses(employeeId)).isEmpty();
    }

    /** Keycloak puts the language picked on its pages in the token; the site's visit copies it. */
    @Test
    void emailsInEnglishOnceTheClientChoseEnglish() throws Exception {
        mockMvc.perform(get("/api/me").with(customer(claims -> claims.put("locale", "en"))))
                .andExpect(status().isOk());
        book();

        Map<String, Object> email = awaitEmail(customerEmail);
        assertThat(JsonPath.<String>read(email, "$.Subject")).isEqualTo("Appointment confirmed");
        assertThat(JsonPath.<String>read(email, "$.Text"))
                .contains("Hello Marie Tremblay,")
                .contains("on " + monday.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                        .withLocale(Locale.forLanguageTag("en-CA"))) + " at 9:00");
    }

    @Test
    void emailsAdminsAboutBugReportsAndSkipsThoseWithoutAnAddress() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID noEmailAdminId = UUID.randomUUID();
        String adminEmail = "admin-" + adminId + "@example.com";
        mockMvc.perform(get("/api/me").with(keycloakJwt(adminId, "admin", "Admin", claims ->
                claims.put("email", adminEmail)))).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(keycloakJwt(noEmailAdminId, "admin", "Admin", claims -> {
        }))).andExpect(status().isOk());

        String reportId = JsonPath.read(send(post("/api/bug-reports"), customer(claims -> {
        }), Map.of("title", "Rien ne marche", "description", "Quand je clique, rien ne se passe.", "pagePath", "/"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        Map<String, Object> email = awaitEmail(adminEmail);
        assertThat(JsonPath.<String>read(email, "$.Subject")).isEqualTo("Nouveau signalement");
        assertThat(JsonPath.<String>read(email, "$.Text"))
                .contains("Marie Tremblay a signalé un problème : « Rien ne marche »")
                .contains("http://localhost:5173/admin/bug-reports/" + reportId);
        await().untilAsserted(() -> assertThat(deliveryStatuses(noEmailAdminId)).containsExactly("SKIPPED"));
    }

    /** Waits for the one email sent to an address and returns it as Mailpit's API shows it. */
    private Map<String, Object> awaitEmail(String address) throws Exception {
        String query = URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8);
        String id = await().until(() -> {
            List<String> ids = JsonPath.read(mailpitApi("/api/v1/search?query=" + query), "$.messages[*].ID");
            return ids.size() == 1 ? ids.getFirst() : null;
        }, found -> found != null);
        return JsonPath.read(mailpitApi("/api/v1/message/" + id), "$");
    }

    private String mailpitApi(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create(TestcontainersConfiguration.mailpitUrl(mailpit) + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private List<String> deliveryStatuses(UUID recipientId) {
        return jdbc.sql("""
                        SELECT d.status FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                        WHERE n.recipient_id = ?""")
                .param(recipientId)
                .query(String.class).list();
    }

    private String book() throws Exception {
        return JsonPath.read(send(post("/api/bookings"), customer(claims -> {
        }), Map.of("employeeId", employeeId, "startsAt", at(9), "serviceIds", List.of(facial.getId())))
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

    private JwtRequestPostProcessor customer(Consumer<Map<String, Object>> claims) {
        return keycloakJwt(customerId, "client", "Marie Tremblay", all -> {
            all.put("email", customerEmail);
            claims.accept(all);
        });
    }

    private JwtRequestPostProcessor employee() {
        return keycloakJwt(employeeId, "employee", "Julie Roy", claims -> claims.put("email", "julie@example.com"));
    }

    private static JwtRequestPostProcessor keycloakJwt(UUID subject, String role, String name,
                                                       Consumer<Map<String, Object>> claims) {
        Map<String, Object> all = new HashMap<>(Map.of(
                "preferred_username", role + "-" + subject,
                "name", name,
                "email_verified", true,
                "realm_access", Map.of("roles", List.of(role))));
        claims.accept(all);
        return jwt()
                .jwt(token -> token.subject(subject.toString()).claims(existing -> existing.putAll(all)))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static String json(@Nullable Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

}
