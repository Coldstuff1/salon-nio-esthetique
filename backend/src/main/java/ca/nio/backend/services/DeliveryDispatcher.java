package ca.nio.backend.services;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ca.nio.backend.model.DeliveryStatus;
import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationDelivery;
import ca.nio.backend.repositories.NotificationDeliveryRepository;
import ca.nio.backend.repositories.NotificationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends queued notification deliveries. Each one is sent in its own transaction, which holds the row's lock
 * while the channel works, and failures are retried with growing pauses (see {@link NotificationDelivery}).
 */
@Slf4j
@Component
public class DeliveryDispatcher {

    /** Deliveries sent in one pass at most, so a backlog doesn't hold the scheduler thread for long. */
    static final int BATCH_SIZE = 50;

    private final NotificationDeliveryRepository deliveries;
    private final NotificationRepository notifications;
    private final TransactionTemplate transactions;
    private final Map<NotificationChannel, ChannelSender> senders = new EnumMap<>(NotificationChannel.class);

    public DeliveryDispatcher(NotificationDeliveryRepository deliveries, NotificationRepository notifications,
                              TransactionTemplate transactions, List<ChannelSender> senders) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.transactions = transactions;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
    }

    @Scheduled(fixedDelayString = "${salon.notifications.dispatch-interval:15s}", initialDelayString = "10s")
    public void dispatch() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            if (!Boolean.TRUE.equals(transactions.execute(tx -> sendNext()))) {
                return;
            }
        }
    }

    /** Sends the most overdue delivery; false when none is due. */
    private boolean sendNext() {
        Optional<NotificationDelivery> due = deliveries.lockNextDue(Instant.now());
        if (due.isEmpty()) {
            return false;
        }
        NotificationDelivery delivery = due.get();
        try {
            Notification notification = notifications.findById(delivery.getNotificationId()).orElseThrow();
            ChannelSender sender = senders.get(delivery.getChannel());
            if (sender == null) {
                throw new UndeliverableException("No sender for " + delivery.getChannel());
            }
            sender.send(notification);
            delivery.sent();
        } catch (UndeliverableException e) {
            delivery.skipped(e.getMessage());
        } catch (RuntimeException e) {
            delivery.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("{} delivery {} failed (attempt {}{})", delivery.getChannel(), delivery.getId(),
                    delivery.getAttempts(), delivery.getStatus() == DeliveryStatus.FAILED ? ", giving up" : "", e);
        }
        deliveries.save(delivery);
        return true;
    }

}
