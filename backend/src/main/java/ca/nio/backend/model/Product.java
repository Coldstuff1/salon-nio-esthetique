package ca.nio.backend.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A retail product the salon sells. Employees attach their own price and stock through {@link ProductOffering}.
 */
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    private UUID id;
    private String name;
    @Nullable
    private String description;
    private String sku;
    private int costCents;
    private boolean active = true;
    @Version
    private Integer version;

    public Product(String name, String description, String sku, int costCents) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.costCents = costCents;
    }

}
