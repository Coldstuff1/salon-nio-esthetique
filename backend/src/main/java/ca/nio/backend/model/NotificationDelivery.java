package ca.nio.backend.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sending one notification over one channel. Failed attempts are retried with growing pauses until
 * {@link #RETRY_DELAYS} runs out.
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {

    /** Pause before each retry; one more failure after the last gives up. */
    public static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));

    /** Error messages are cut to fit the column. */
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;
    private UUID notificationId;
    private NotificationChannel channel;
    private DeliveryStatus status;
    private int attempts;
    private Instant nextAttemptAt;
    private @Nullable String lastError;
    private @Nullable Instant sentAt;
    private Instant createdAt;
    @Version
    private Integer version;

    public NotificationDelivery(UUID notificationId, NotificationChannel channel) {
        this.id = UUID.randomUUID();
        this.notificationId = notificationId;
        this.channel = channel;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.nextAttemptAt = createdAt;
    }

    public void sent() {
        attempts++;
        status = DeliveryStatus.SENT;
        sentAt = Instant.now();
        lastError = null;
    }

    public void skipped(String reason) {
        status = DeliveryStatus.SKIPPED;
        lastError = truncate(reason);
    }

    /** Schedules the next attempt, or gives up once the retries are used. */
    public void failed(String error) {
        attempts++;
        lastError = truncate(error);
        if (attempts > RETRY_DELAYS.size()) {
            status = DeliveryStatus.FAILED;
        } else {
            nextAttemptAt = Instant.now().plus(RETRY_DELAYS.get(attempts - 1));
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

}
