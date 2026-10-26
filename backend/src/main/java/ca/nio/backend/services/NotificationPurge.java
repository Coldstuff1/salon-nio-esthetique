package ca.nio.backend.services;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.repositories.NotificationDeliveryRepository;
import ca.nio.backend.repositories.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Deletes notifications and delivery records past their retention ({@code salon.notifications.retention}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPurge {

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final NotificationProperties properties;

    @Scheduled(cron = "${salon.notifications.retention.cron:0 30 3 * * *}", zone = "${salon.zone:America/Toronto}")
    public void run() {
        purge(Instant.now());
    }

    /** A notification's deliveries go with it; pending ones are never touched on their own. */
    @Transactional
    public void purge(Instant now) {
        NotificationProperties.Retention retention = properties.retention();
        int removed = notifications.deleteOld(now.minus(retention.read()), now.minus(retention.unread()));
        int records = deliveries.deleteFinishedBefore(now.minus(retention.deliveries()));
        if (removed > 0 || records > 0) {
            log.info("Purged {} old notifications and {} delivery records", removed, records);
        }
    }

}
