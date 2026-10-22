package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * App-side profile of a client. The id is the Keycloak subject; credentials live in Keycloak only.
 */
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    private UUID id;
    private String displayName;
    private String email;
    private String phone;
    private Instant createdAt;
    @Version
    private Integer version;

    public Customer(UUID id, String displayName, String email) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.createdAt = Instant.now();
    }

}
