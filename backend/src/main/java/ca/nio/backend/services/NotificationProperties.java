package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param siteUrl          the site's public address; links in emails point there
 * @param fromAddress      sender of notification emails; the mail provider must accept it (SPF, DKIM)
 * @param fromName         name shown next to the sender address
 * @param dispatchInterval pause between two passes of the {@link DeliveryDispatcher}
 */
@ConfigurationProperties("salon.notifications")
public record NotificationProperties(@DefaultValue("http://localhost:5173") URI siteUrl,
                                     @DefaultValue("no-reply@localhost") String fromAddress,
                                     @DefaultValue("Salon Nio Esthétique") String fromName,
                                     @DefaultValue("15s") Duration dispatchInterval) {
}
