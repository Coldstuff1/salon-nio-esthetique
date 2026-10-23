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
 * A retail product the salon sells. Employees attach their own price, cost and stock through
 * {@link ProductOffering}.
 */
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    private UUID id;
    @Embedded.Nullable(prefix = "name_")
    private LocalizedText name;
    @Nullable
    @Embedded.Nullable(prefix = "description_")
    private LocalizedText description;
    private String sku;
    private boolean active = true;
    @Version
    private Integer version;

    public Product(LocalizedText name, @Nullable LocalizedText description, String sku) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.sku = sku;
    }

}
