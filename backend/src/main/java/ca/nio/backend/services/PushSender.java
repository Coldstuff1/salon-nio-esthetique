package ca.nio.backend.services;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import ca.nio.backend.model.Notification;
import ca.nio.backend.model.NotificationChannel;
import ca.nio.backend.model.NotificationRecipient;
import ca.nio.backend.model.PushSubscription;
import ca.nio.backend.repositories.NotificationRecipientRepository;
import ca.nio.backend.repositories.PushSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pushes a notification to every browser the recipient turned push on in, in their language; the site's service
 * worker ({@code frontend/src/sw.ts}) shows it. It counts as sent once any browser's push service took it, so a
 * retry never shows it twice on the others; browsers that unsubscribed are forgotten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSender implements ChannelSender {

    /** Keeps the message well under {@link WebPushClient#MAX_PAYLOAD}, whatever a bug report's title. */
    private static final int MAX_BODY = 1000;

    private final WebPushClient push;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationRecipientRepository recipients;
    private final NotificationMessages messages;
    private final JsonMapper json;

    /**
     * What the service worker receives.
     *
     * @param id   the notification's id; a second message with the same one replaces the first on screen
     * @param link site path to open when it's clicked
     */
    record Payload(String id, String title, String body, @Nullable String link) {
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public void send(Notification notification) {
        List<PushSubscription> browsers = subscriptions.findByRecipientId(notification.getRecipientId());
        if (browsers.isEmpty()) {
            throw new UndeliverableException("Push isn't on anywhere for " + notification.getRecipientId());
        }
        Locale locale = NotificationMessages.locale(recipients.findById(notification.getRecipientId())
                .map(NotificationRecipient::getLocale).orElse(null));
        NotificationMessages.Text text = messages.text(notification, locale);
        String body = text.body().length() <= MAX_BODY ? text.body() : text.body().substring(0, MAX_BODY - 1) + "…";
        byte[] payload = json.writeValueAsBytes(
                new Payload(notification.getId().toString(), text.title(), body, notification.getLink()));

        boolean delivered = false;
        String retryReason = null;
        for (PushSubscription browser : browsers) {
            String host = URI.create(browser.getEndpoint()).getHost();
            int status;
            try {
                status = push.send(browser, payload);
            } catch (IOException e) {
                retryReason = host + ": " + e;
                continue;
            }
            if (status >= 200 && status < 300) {
                delivered = true;
                subscriptions.markSucceeded(browser.getId(), Instant.now());
            } else if (status == 404 || status == 410) {
                // the browser unsubscribed, or the person cleared the site's data
                subscriptions.delete(browser.getId());
            } else if (status == 429 || status >= 500) {
                retryReason = host + " answered " + status;
            } else {
                // e.g. 400/403/413: this message will never be taken, sending it again won't help
                log.warn("Push service {} refused notification {} with {}", host, notification.getId(), status);
            }
        }
        if (delivered) {
            return;
        }
        if (retryReason != null) {
            throw new IllegalStateException(retryReason);
        }
        throw new UndeliverableException("No push service took it");
    }

}
