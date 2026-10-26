package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import ca.nio.backend.model.LocalizedText;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;
import ca.nio.backend.services.TestBrowser;

/**
 * Notifications whose type calls for push reach every browser the person subscribed, through a stand-in push
 * service on this machine: encrypted for that browser, with a VAPID token for that service, in their language.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PushNotificationIntegrationTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");

    /** What the stand-in push service received, by request path. */
    record Received(Map<String, List<String>> headers, byte[] body) {

        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
        }

    }

    static HttpServer pushService;
    static final Map<String, Received> received = new ConcurrentHashMap<>();

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ServiceTypeRepository serviceTypes;
    @Autowired
    JdbcClient jdbc;
    @Value("${salon.notifications.push.public-key}")
    String vapidPublicKey;

    UUID employeeId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    ServiceType facial;
    LocalDate monday = LocalDate.now(ZONE).plusWeeks(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    /** Takes every message (201), except under /gone/, as for a browser that unsubscribed (410). */
    @BeforeAll
    static void startPushService() throws IOException {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext("/", exchange -> {
            received.put(exchange.getRequestURI().getPath(),
                    new Received(Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/gone/") ? 410 : 201, -1);
            exchange.close();
        });
        pushService.start();
    }

    @AfterAll
    static void stopPushService() {
        pushService.stop(0);
    }

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

    @Test
    void pushesANewBookingToTheEmployeesBrowser() throws Exception {
        TestBrowser browser = new TestBrowser();
        String path = "/push/" + UUID.randomUUID();
        subscribe(employee(), endpoint(path), browser).andExpect(status().isNoContent());

        book();

        Received message = await().until(() -> received.get(path), found -> found != null);
        assertThat(message.header("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(message.header("TTL")).isEqualTo("86400");
        Matcher vapid = Pattern.compile("vapid t=([^,]+), k=(.+)").matcher(message.header("Authorization"));
        assertThat(vapid.matches()).isTrue();
        assertThat(vapid.group(2)).isEqualTo(vapidPublicKey);
        SignedJWT token = SignedJWT.parse(vapid.group(1));
        assertThat(token.verify(new ECDSAVerifier(vapidKey()))).isTrue();
        assertThat(token.getJWTClaimsSet().getAudience()).containsExactly("http://localhost:" + port());

        String payload = new String(browser.decrypt(message.body()), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(payload, "$.title")).isEqualTo("Nouveau rendez-vous");
        assertThat(JsonPath.<String>read(payload, "$.body"))
                .startsWith("Marie Tremblay a pris rendez-vous avec vous le lundi " + monday.getDayOfMonth());
        assertThat(JsonPath.<String>read(payload, "$.link")).isEqualTo("/staff?day=" + monday);
        await().untilAsserted(() -> assertThat(pushStatuses(employeeId)).containsExactly("SENT"));
        assertThat(jdbc.sql("SELECT last_success_at FROM push_subscription WHERE endpoint = ?")
                .param(endpoint(path)).query(Instant.class).single()).isNotNull();
        // the client's confirmation isn't pushed by default
        assertThat(pushStatuses(customerId)).isEmpty();
    }

    @Test
    void forgetsBrowsersThatUnsubscribed() throws Exception {
        String path = "/gone/" + UUID.randomUUID();
        subscribe(employee(), endpoint(path), new TestBrowser()).andExpect(status().isNoContent());

        book();

        await().untilAsserted(() -> assertThat(pushStatuses(employeeId)).containsExactly("SKIPPED"));
        assertThat(received).containsKey(path);
        assertThat(subscriptionOwner(endpoint(path))).isNull();
    }

    @Test
    void skipsPushForSomeoneWithoutASubscribedBrowser() throws Exception {
        book();

        await().untilAsserted(() -> assertThat(pushStatuses(employeeId)).containsExactly("SKIPPED"));
    }

    @Test
    void refusesUnknownPushServicesAndBadKeys() throws Exception {
        TestBrowser browser = new TestBrowser();

        for (String endpoint : List.of("https://push.example.com/abc", "http://169.254.169.254/latest",
                "https://localhost.example.com/abc", "ftp://localhost/abc")) {
            subscribe(employee(), endpoint, browser)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("unsupported-push-service"));
        }
        send(put("/api/me/push-subscriptions"), employee(), Map.of("endpoint", endpoint("/push/x"),
                "keys", Map.of("p256dh", "AAAA", "auth", browser.auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
    }

    @Test
    void aBrowserBelongsToWhoeverSubscribedLastAndOnlyTheyCanRemoveIt() throws Exception {
        String endpoint = endpoint("/push/" + UUID.randomUUID());
        subscribe(employee(), endpoint, new TestBrowser()).andExpect(status().isNoContent());
        subscribe(customer(), endpoint, new TestBrowser()).andExpect(status().isNoContent());
        assertThat(subscriptionOwner(endpoint)).isEqualTo(customerId);

        mockMvc.perform(delete("/api/me/push-subscriptions").param("endpoint", endpoint).with(employee()))
                .andExpect(status().isNoContent());
        assertThat(subscriptionOwner(endpoint)).isEqualTo(customerId);

        mockMvc.perform(delete("/api/me/push-subscriptions").param("endpoint", endpoint).with(customer()))
                .andExpect(status().isNoContent());
        assertThat(subscriptionOwner(endpoint)).isNull();
    }

    @Test
    void publishesTheVapidKeyToAnyone() throws Exception {
        mockMvc.perform(get("/api/public/push-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value(vapidPublicKey));
    }

    private ResultActions subscribe(JwtRequestPostProcessor auth, String endpoint, TestBrowser browser)
            throws Exception {
        return send(put("/api/me/push-subscriptions"), auth,
                Map.of("endpoint", endpoint, "keys", Map.of("p256dh", browser.p256dh(), "auth", browser.auth())));
    }

    private static int port() {
        return pushService.getAddress().getPort();
    }

    private static String endpoint(String path) {
        return "http://localhost:" + port() + path;
    }

    private ECKey vapidKey() {
        byte[] point = Base64URL.from(vapidPublicKey).decode();
        return new ECKey.Builder(Curve.P_256,
                Base64URL.encode(Arrays.copyOfRange(point, 1, 33)),
                Base64URL.encode(Arrays.copyOfRange(point, 33, 65))).build();
    }

    private List<String> pushStatuses(UUID recipientId) {
        return jdbc.sql("""
                        SELECT d.status FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                        WHERE n.recipient_id = ? AND d.channel = 'PUSH'""")
                .param(recipientId)
                .query(String.class).list();
    }

    private @Nullable UUID subscriptionOwner(String endpoint) {
        return jdbc.sql("SELECT recipient_id FROM push_subscription WHERE endpoint = ?")
                .param(endpoint).query(UUID.class).optional().orElse(null);
    }

    private void book() throws Exception {
        send(post("/api/bookings"), customer(), Map.of("employeeId", employeeId, "startsAt", at(9),
                "serviceIds", List.of(facial.getId())))
                .andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor auth, Object body)
            throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(body)).with(auth));
    }

    private Instant at(int hour) {
        return ZonedDateTime.of(monday, LocalTime.of(hour, 0), ZONE).toInstant();
    }

    private JwtRequestPostProcessor customer() {
        return keycloakJwt(customerId, "client", "Marie Tremblay", "marie-" + customerId + "@example.com");
    }

    private JwtRequestPostProcessor employee() {
        return keycloakJwt(employeeId, "employee", "Julie Roy", "julie@example.com");
    }

    private static JwtRequestPostProcessor keycloakJwt(UUID subject, String role, String name, String email) {
        return jwt()
                .jwt(token -> token.subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("name", name)
                        .claim("email", email)
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

    private static String json(@Nullable Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

}
