package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import ca.nio.backend.model.Notification;

public interface NotificationRepository extends Repository<Notification, UUID> {

    /** Adds the notification unless one with the same dedupe key exists; returns the rows inserted (0 or 1). */
    @Modifying
    @Query("""
            INSERT INTO notification (id, recipient_id, type, params, link, dedupe_key, created_at)
            VALUES (:id, :recipientId, :type, :params, :link, :dedupeKey, :createdAt)
            ON CONFLICT (dedupe_key) DO NOTHING""")
    int insertIfNew(UUID id, UUID recipientId, String type, String params, @Nullable String link, String dedupeKey,
                    Instant createdAt);

    Optional<Notification> findById(UUID id);

    @Query("SELECT * FROM notification WHERE recipient_id = :recipientId ORDER BY created_at DESC LIMIT :limit")
    List<Notification> latest(UUID recipientId, int limit);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE notification SET read_at = :readAt WHERE id = :id AND recipient_id = :recipientId AND read_at IS NULL")
    int markRead(UUID id, UUID recipientId, Instant readAt);

    @Modifying
    @Query("UPDATE notification SET read_at = :readAt WHERE recipient_id = :recipientId AND read_at IS NULL")
    int markAllRead(UUID recipientId, Instant readAt);

}
