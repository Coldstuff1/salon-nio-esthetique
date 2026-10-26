package ca.nio.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    @Test
    void retriesWithGrowingPausesThenGivesUp() {
        NotificationDelivery delivery = new NotificationDelivery(UUID.randomUUID(), NotificationChannel.EMAIL);

        for (Duration delay : NotificationDelivery.RETRY_DELAYS) {
            Instant before = Instant.now();
            delivery.failed("Connection refused");
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.getNextAttemptAt()).isBetween(before.plus(delay), Instant.now().plus(delay));
        }
        delivery.failed("Connection refused");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(NotificationDelivery.RETRY_DELAYS.size() + 1);
        assertThat(delivery.getLastError()).isEqualTo("Connection refused");
    }

    @Test
    void cutsLongErrorsToFitTheColumn() {
        NotificationDelivery delivery = new NotificationDelivery(UUID.randomUUID(), NotificationChannel.EMAIL);

        delivery.failed("x".repeat(2000));

        assertThat(delivery.getLastError()).hasSize(500);
    }

}
