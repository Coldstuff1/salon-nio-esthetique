package ca.nio.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationDelivery;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.repositories.NotificationDeliveryRepository;
import ca.nio.backend.repositories.NotificationRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes and reads people's notifications. Text isn't stored: each reader's client renders it from the type and
 * params in their language.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** How many of the latest notifications the list shows. */
    static final int INBOX_SIZE = 50;

    private static final TypeReference<Map<String, String>> PARAMS = new TypeReference<>() {
    };

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final JsonMapper json;

    /**
     * Notifies one person, unless a notification with the same dedupe key exists already (an event delivered
     * twice, a job that runs again). Besides the in-app list, it's queued for the type's channels, which the
     * {@link DeliveryDispatcher} sends shortly after.
     *
     * @param dedupeKey names the occurrence, e.g. {@code STAFF_BOOKING_CREATED:<bookingId>:<recipientId>}
     */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, Map<String, String> params, @Nullable String link,
                       String dedupeKey) {
        UUID id = UUID.randomUUID();
        if (notifications.insertIfNew(id, recipientId, type.name(), json.writeValueAsString(params), link, dedupeKey,
                Instant.now()) == 0) {
            return;
        }
        for (NotificationChannel channel : type.defaultChannels()) {
            deliveries.save(new NotificationDelivery(id, channel));
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID recipientId) {
        return notifications.latest(recipientId, INBOX_SIZE);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notifications.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    /** Marks one of the person's own notifications read; reading one twice is fine. */
    @Transactional
    public void markRead(UUID notificationId, UUID recipientId) {
        if (notifications.markRead(notificationId, recipientId, Instant.now()) == 0
                && !notifications.existsByIdAndRecipientId(notificationId, recipientId)) {
            throw new NotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification " + notificationId + " not found");
        }
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        notifications.markAllRead(recipientId, Instant.now());
    }

    public Map<String, String> params(Notification notification) {
        return json.readValue(notification.getParams(), PARAMS);
    }

}
