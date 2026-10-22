package ca.nio.backend.model;

import java.util.UUID;

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
    private String description;
    private String sku;
    private boolean active = true;
    @Version
    private Integer version;

    public Product(String name, String description, String sku) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.sku = sku;
    }

}
