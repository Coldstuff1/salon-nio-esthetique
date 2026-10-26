package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param siteUrl          the site's public address; links in emails point there
 * @param fromAddress      sender of notification emails; the mail provider must accept it (SPF, DKIM)
 * @param fromName         name shown next to the sender address
 * @param dispatchInterval pause between two passes of the {@link DeliveryDispatcher}
 * @param push             Web Push settings
 */
@ConfigurationProperties("salon.notifications")
public record NotificationProperties(@DefaultValue("http://localhost:5173") URI siteUrl,
                                     @DefaultValue("no-reply@localhost") String fromAddress,
                                     @DefaultValue("Salon Nio Esthétique") String fromName,
                                     @DefaultValue("15s") Duration dispatchInterval,
                                     @DefaultValue Push push) {

    /**
     * @param publicKey    the VAPID public key (RFC 8292): an uncompressed P-256 point, base64url. Browsers
     *                     subscribe with it, so changing it silently orphans every subscription.
     * @param privateKey   its private key, base64url; signs the token sent with each message
     * @param subject      how push services can reach the salon about its traffic, {@code mailto:} or {@code https:}
     * @param ttl          how long a push service keeps a message for a browser that's offline
     * @param allowedHosts push services the backend sends to, by host or parent domain; browsers with any other
     *                     address are refused, so a crafted subscription can't make the backend call elsewhere
     */
    public record Push(@Nullable String publicKey,
                       @Nullable String privateKey,
                       @DefaultValue("mailto:no-reply@localhost") String subject,
                       @DefaultValue("24h") Duration ttl,
                       @DefaultValue({"fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com",
                               "notify.windows.com"}) List<String> allowedHosts) {
    }

}
