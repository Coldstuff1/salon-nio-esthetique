package ca.nio.backend.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Embedded;

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
    @Embedded.Nullable(prefix = "name_")
    private LocalizedText name;
    @Nullable
    @Embedded.Nullable(prefix = "description_")
    private LocalizedText description;
    @Nullable
    @Embedded.Nullable(prefix = "category_")
    private LocalizedText category;
    private boolean active = true;
    @Version
    private Integer version;

    public ServiceType(LocalizedText name, @Nullable LocalizedText description, @Nullable LocalizedText category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.category = category;
    }

}
