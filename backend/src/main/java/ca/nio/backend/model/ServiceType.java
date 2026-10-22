package ca.nio.backend.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service the salon offers (e.g. "Facial"). Employees attach their own price and duration through
 * {@link ServiceOffering}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceType {

    @Id
    private UUID id;
    private String name;
    private String description;
    private String category;
    private boolean active = true;
    @Version
    private Integer version;

    public ServiceType(String name, String description, String category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.category = category;
    }

}
