package ca.nio.backend.services;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationChannel;

/** Sends notifications over one channel, for the {@link DeliveryDispatcher}. */
public interface ChannelSender {

    NotificationChannel channel();

    /**
     * @throws UndeliverableException when there's nowhere to send it; any other exception is retried later
     */
    void send(Notification notification);

}
