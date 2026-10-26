package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One item in someone's notification list. Rows are written only through
 * {@code NotificationRepository.insertIfNew}, so a repeated occurrence (same dedupe key) is dropped.
 */
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    private UUID id;
    /** Keycloak subject of the person notified. */
    private UUID recipientId;
    private NotificationType type;
    /** JSON object of strings the text is built from; see {@link NotificationType}. */
    private String params;
    /** Site path to open, e.g. {@code /account/bookings/<id>}. */
    private @Nullable String link;
    private String dedupeKey;
    private Instant createdAt;
    private @Nullable Instant readAt;

}
