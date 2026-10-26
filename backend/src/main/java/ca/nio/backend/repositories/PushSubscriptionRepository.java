package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import ca.nio.backend.model.PushSubscription;

public interface PushSubscriptionRepository extends Repository<PushSubscription, UUID> {

    /** Adds the browser, or gives it to this person with fresh keys if it subscribed before. */
    @Modifying
    @Query("""
            INSERT INTO push_subscription (id, recipient_id, endpoint, p256dh, auth, user_agent, created_at)
            VALUES (:id, :recipientId, :endpoint, :p256dh, :auth, :userAgent, now())
            ON CONFLICT (endpoint) DO UPDATE SET
                recipient_id = EXCLUDED.recipient_id,
                p256dh = EXCLUDED.p256dh,
                auth = EXCLUDED.auth,
                user_agent = EXCLUDED.user_agent""")
    int upsert(UUID id, UUID recipientId, String endpoint, String p256dh, String auth, @Nullable String userAgent);

    List<PushSubscription> findByRecipientId(UUID recipientId);

    /** Removes one of the person's own browsers; returns the rows deleted (0 or 1). */
    @Modifying
    @Query("DELETE FROM push_subscription WHERE endpoint = :endpoint AND recipient_id = :recipientId")
    int deleteOwn(String endpoint, UUID recipientId);

    @Modifying
    @Query("DELETE FROM push_subscription WHERE id = :id")
    void delete(UUID id);

    @Modifying
    @Query("UPDATE push_subscription SET last_success_at = :at WHERE id = :id")
    void markSucceeded(UUID id, Instant at);

}
