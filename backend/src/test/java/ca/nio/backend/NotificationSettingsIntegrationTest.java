package ca.nio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ca.nio.backend.model.NotificationType;
import ca.nio.backend.security.KeycloakJwtAuthenticationConverter;
import ca.nio.backend.services.NotificationPurge;
import ca.nio.backend.services.NotificationService;
import ca.nio.backend.services.NotificationSettingsService;

/**
 * People choose which notifications they get by email and push; the choices, an email's unsubscribe link, and the
 * retention purge.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSettingsIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    NotificationService notifications;
    @Autowired
    NotificationSettingsService settings;
    @Autowired
    NotificationPurge purge;

    UUID clientId = UUID.randomUUID();

    @Test
    void listsTheTypesTheirRolesGetWithTheDefaults() throws Exception {
        String body = mockMvc.perform(get("/api/me/notification-settings").with(caller(clientId, "client")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> types = JsonPath.read(body, "$[?(@.channel == 'EMAIL')].type");
        assertThat(types).containsExactly("BOOKING_CONFIRMED", "BOOKING_CANCELLED", "BOOKING_REMINDER",
                "BUG_REPORT_CLOSED");
        assertThat(setting(body, "BOOKING_CONFIRMED", "EMAIL")).containsEntry("enabled", true)
                .containsEntry("byDefault", true);
        assertThat(setting(body, "BOOKING_CONFIRMED", "PUSH")).containsEntry("enabled", false)
                .containsEntry("byDefault", false);
    }

    @Test
    void sendsOverTheChannelsThePersonChose() throws Exception {
        String body = choose(clientId, "client", List.of(
                Map.of("type", "BOOKING_CONFIRMED", "channel", "EMAIL", "enabled", false),
                Map.of("type", "BOOKING_CONFIRMED", "channel", "PUSH", "enabled", true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(setting(body, "BOOKING_CONFIRMED", "EMAIL")).containsEntry("enabled", false);

        UUID notificationId = notify(clientId, NotificationType.BOOKING_CONFIRMED);

        assertThat(channels(notificationId)).containsExactly("PUSH");
    }

    @Test
    void storesOnlyChoicesThatDifferFromTheDefault() throws Exception {
        choose(clientId, "client", List.of(Map.of("type", "BOOKING_REMINDER", "channel", "PUSH", "enabled", false)))
                .andExpect(status().isOk());
        assertThat(storedChoices(clientId)).isEqualTo(1);

        choose(clientId, "client", List.of(Map.of("type", "BOOKING_REMINDER", "channel", "PUSH", "enabled", true)))
                .andExpect(status().isOk());
        assertThat(storedChoices(clientId)).isZero();
    }

    @Test
    void refusesTypesTheirRolesNeverGet() throws Exception {
        choose(clientId, "client", List.of(Map.of("type", "BUG_REPORT_SUBMITTED", "channel", "EMAIL",
                "enabled", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-request"));
    }

    @Test
    void anUnsubscribeLinkStopsThatTypeOfEmailWithoutSigningIn() throws Exception {
        String token = settings.unsubscribeToken(clientId, NotificationType.BOOKING_REMINDER);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/public/unsubscribe").param("token", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("BOOKING_REMINDER"));
        }

        assertThat(channels(notify(clientId, NotificationType.BOOKING_REMINDER))).containsExactly("PUSH");
        assertThat(channels(notify(clientId, NotificationType.BOOKING_CONFIRMED))).containsExactly("EMAIL");
    }

    @Test
    void refusesForgedUnsubscribeLinks() throws Exception {
        String token = settings.unsubscribeToken(clientId, NotificationType.BOOKING_REMINDER);
        String otherType = token.replace(".BOOKING_REMINDER.", ".BOOKING_CONFIRMED.");
        String otherPerson = UUID.randomUUID() + token.substring(token.indexOf('.'));

        for (String forged : List.of(otherType, otherPerson, "nonsense", "a.b.c")) {
            mockMvc.perform(post("/api/public/unsubscribe").param("token", forged))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid-unsubscribe-link"));
        }
        assertThat(storedChoices(clientId)).isZero();
    }

    @Test
    void purgesOldNotificationsAndDeliveryRecords() {
        Instant now = Instant.now();
        UUID readLongAgo = notification(now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(91)));
        UUID readRecently = notification(now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(10)));
        UUID unreadOld = notification(now.minus(Duration.ofDays(400)), null);
        UUID unreadRecent = notification(now.minus(Duration.ofDays(200)), null);
        delivery(readRecently, "SENT", now.minus(Duration.ofDays(31)));
        delivery(unreadRecent, "PENDING", now.minus(Duration.ofDays(31)));
        UUID recentDelivery = delivery(unreadRecent, "FAILED", now.minus(Duration.ofDays(5)));

        purge.purge(now);

        assertThat(jdbc.sql("SELECT id FROM notification WHERE recipient_id = ?").param(clientId)
                .query(UUID.class).list()).containsExactlyInAnyOrder(readRecently, unreadRecent)
                .doesNotContain(readLongAgo, unreadOld);
        assertThat(jdbc.sql("""
                        SELECT d.status FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                        WHERE n.recipient_id = ?""").param(clientId).query(String.class).list())
                .containsExactlyInAnyOrder("PENDING", "FAILED");
        assertThat(jdbc.sql("SELECT count(*) FROM notification_delivery WHERE id = ?").param(recentDelivery)
                .query(Integer.class).single()).isOne();
    }

    private UUID notify(UUID recipientId, NotificationType type) {
        String key = "test:" + UUID.randomUUID();
        notifications.notify(recipientId, type, Map.of(), null, key);
        return jdbc.sql("SELECT id FROM notification WHERE dedupe_key = ?").param(key).query(UUID.class).single();
    }

    private UUID notification(Instant createdAt, @Nullable Instant readAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO notification (id, recipient_id, type, params, dedupe_key, created_at, read_at)
                        VALUES (?, ?, 'BOOKING_CONFIRMED', '{}', ?, ?, ?)""")
                .params(id, clientId, "test:" + id, Timestamp.from(createdAt),
                        readAt == null ? null : Timestamp.from(readAt))
                .update();
        return id;
    }

    private UUID delivery(UUID notificationId, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO notification_delivery (id, notification_id, channel, status, next_attempt_at,
                                                           created_at)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .params(id, notificationId, "PENDING".equals(status) ? "PUSH" : "EMAIL", status,
                        Timestamp.from(createdAt.plus(Duration.ofDays(1000))), Timestamp.from(createdAt))
                .update();
        return id;
    }

    private List<String> channels(UUID notificationId) {
        return jdbc.sql("SELECT channel FROM notification_delivery WHERE notification_id = ?")
                .param(notificationId).query(String.class).list();
    }

    private int storedChoices(UUID recipientId) {
        return jdbc.sql("SELECT count(*) FROM notification_preference WHERE recipient_id = ?")
                .param(recipientId).query(Integer.class).single();
    }

    private static Map<String, Object> setting(String body, String type, String channel) {
        List<Map<String, Object>> found = JsonPath.read(body,
                "$[?(@.type == '" + type + "' && @.channel == '" + channel + "')]");
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private ResultActions choose(UUID subject, String role, List<Map<String, Object>> choices) throws Exception {
        return mockMvc.perform(put("/api/me/notification-settings").with(caller(subject, role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(choices)));
    }

    private static JwtRequestPostProcessor caller(UUID subject, String role) {
        return jwt()
                .jwt(token -> token.subject(subject.toString())
                        .claim("preferred_username", role + "-" + subject)
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakJwtAuthenticationConverter()::authorities);
    }

}
