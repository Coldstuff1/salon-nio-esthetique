package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How to reach someone, copied from their token whenever the site loads. This is the only place employees' and
 * admins' emails and roles are kept, so someone who has never signed in can't be notified.
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecipient {

    /** The Keycloak subject. */
    @Id
    private UUID id;
    private @Nullable String email;
    private String displayName;
    /** Comma-separated app roles, e.g. {@code client} or {@code employee,admin}. */
    private String roles;
    /** {@code fr} or {@code en}. */
    private String locale;
    private Instant updatedAt;

}
