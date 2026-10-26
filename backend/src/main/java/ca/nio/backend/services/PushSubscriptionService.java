package ca.nio.backend.services;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.repositories.PushSubscriptionRepository;

import lombok.RequiredArgsConstructor;

/** Browsers people turned push notifications on in; the {@link PushSender} sends to them. */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private static final int MAX_USER_AGENT = 255;

    private final PushSubscriptionRepository subscriptions;
    private final NotificationProperties properties;

    /**
     * Remembers the browser for this person. Subscribing again (new keys, or someone else signed in on the same
     * browser) replaces what was stored for it.
     *
     * @param endpoint the push service's address for the browser; only known push services are accepted
     * @param p256dh   the browser's public key, base64url
     * @param auth     the browser's authentication secret, base64url
     */
    @Transactional
    public void subscribe(UUID recipientId, String endpoint, String p256dh, String auth, @Nullable String userAgent) {
        checkEndpoint(endpoint);
        byte[] key = decode(p256dh);
        byte[] secret = decode(auth);
        if (key == null || !WebPushClient.isValidPublicKey(key)) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "p256dh is not a P-256 public key");
        }
        if (secret == null || secret.length != 16) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "auth is not a 16-byte secret");
        }
        String agent = userAgent == null || userAgent.length() <= MAX_USER_AGENT ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT);
        subscriptions.upsert(UUID.randomUUID(), recipientId, endpoint, p256dh, auth, agent);
    }

    /** Forgets one of the person's browsers; one that isn't theirs, or is already gone, is left alone. */
    @Transactional
    public void unsubscribe(UUID recipientId, String endpoint) {
        subscriptions.deleteOwn(endpoint, recipientId);
    }

    private void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new InvalidRequestException(ErrorCode.INVALID_REQUEST, "endpoint is not a URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        // plain HTTP only for a push service on this machine, which only tests allow
        boolean secure = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && host.equals("localhost");
        boolean allowed = properties.push().allowedHosts().stream()
                .map(allowedHost -> allowedHost.toLowerCase(Locale.ROOT))
                .anyMatch(allowedHost -> host.equals(allowedHost) || host.endsWith("." + allowedHost));
        if (!secure || !allowed || uri.getRawUserInfo() != null) {
            throw new InvalidRequestException(ErrorCode.UNSUPPORTED_PUSH_SERVICE,
                    "Push service " + host + " is not supported", Map.of("host", host));
        }
    }

    private static byte @Nullable [] decode(String base64url) {
        try {
            return WebPushClient.decode(base64url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
