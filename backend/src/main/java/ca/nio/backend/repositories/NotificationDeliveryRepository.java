package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import ca.nio.backend.model.NotificationDelivery;

public interface NotificationDeliveryRepository extends Repository<NotificationDelivery, UUID> {

    NotificationDelivery save(NotificationDelivery delivery);

    /**
     * The most overdue pending delivery, locked until the transaction ends; rows another transaction holds are
     * passed over, so two dispatchers never send the same one.
     */
    @Query("""
            SELECT * FROM notification_delivery
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED""")
    Optional<NotificationDelivery> lockNextDue(Instant now);

    List<NotificationDelivery> findByNotificationId(UUID notificationId);

    @Modifying
    @Query("DELETE FROM notification_delivery WHERE created_at < :before AND status <> 'PENDING'")
    int deleteFinishedBefore(Instant before);

}
