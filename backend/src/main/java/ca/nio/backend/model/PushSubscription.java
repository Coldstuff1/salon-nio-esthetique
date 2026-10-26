package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A browser that shows someone's notifications: where its push service takes messages for it, and the keys
 * they're encrypted with (RFC 8291). Written only through {@code PushSubscriptionRepository.upsert}.
 */
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription {

    @Id
    private UUID id;
    /** Keycloak subject of the person signed in when the browser subscribed. */
    private UUID recipientId;
    private String endpoint;
    /** The browser's P-256 public key, uncompressed, base64url. */
    private String p256dh;
    /** The browser's 16-byte authentication secret, base64url. */
    private String auth;
    private @Nullable String userAgent;
    private Instant createdAt;
    private @Nullable Instant lastSuccessAt;

}
