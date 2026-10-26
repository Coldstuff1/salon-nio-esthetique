package ca.nio.backend.services;

import java.net.URI;
import java.time.Duration;
import java.time.LocalTime;
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
 * @param jobs             the scheduled notifications ({@link ReminderJobs})
 * @param retention        how long notifications are kept ({@link NotificationPurge})
 * @param unsubscribeSecret signs the unsubscribe links in emails; changing it voids every link already sent
 */
@ConfigurationProperties("salon.notifications")
public record NotificationProperties(@DefaultValue("http://localhost:5173") URI siteUrl,
                                     @DefaultValue("no-reply@localhost") String fromAddress,
                                     @DefaultValue("Salon Nio Esthétique") String fromName,
                                     @DefaultValue("15s") Duration dispatchInterval,
                                     @DefaultValue Push push,
                                     @DefaultValue Jobs jobs,
                                     @DefaultValue Retention retention,
                                     @Nullable String unsubscribeSecret) {

    /**
     * @param cron        when {@link ReminderJobs} looks for something to send, in the salon's time zone; {@code -}
     *                    turns them off (tests run them by hand)
     * @param reminder    how long before an appointment the client is reminded of it; bookings made later than
     *                    that get no reminder, their confirmation is recent enough
     * @param agendaTime  when employees get their day's agenda, salon time
     * @param openBooking how long after an appointment ends its employee is asked to close it, if they haven't
     */
    public record Jobs(@DefaultValue("0 */5 * * * *") String cron,
                       @DefaultValue("24h") Duration reminder,
                       @DefaultValue("07:00") LocalTime agendaTime,
                       @DefaultValue("2h") Duration openBooking) {
    }

    /**
     * Notifications are kept only as long as they're useful (Law 25: personal information is kept no longer than
     * needed).
     *
     * @param cron       when the purge runs, in the salon's time zone; {@code -} turns it off
     * @param read       how long a notification is kept once read
     * @param unread     how long one nobody read is kept
     * @param deliveries how long the record of an email or push sent (or not) is kept
     */
    public record Retention(@DefaultValue("0 30 3 * * *") String cron,
                            @DefaultValue("90d") Duration read,
                            @DefaultValue("365d") Duration unread,
                            @DefaultValue("30d") Duration deliveries) {
    }

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
