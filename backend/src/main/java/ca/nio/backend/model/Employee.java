package ca.nio.backend.model;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Aggregate root for everything an employee controls: working hours, time off, and their priced offerings.
 * The id is the Keycloak subject.
 */
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    private UUID id;
    private String displayName;
    private String bio;
    private boolean active = true;
    /** Optional profile photo, a {@link Media} id. */
    @Nullable
    private UUID photoId;
    @Version
    private Integer version;

    @MappedCollection(idColumn = "employee_id")
    private Set<Availability> availability = new HashSet<>();

    @MappedCollection(idColumn = "employee_id")
    private Set<TimeOff> timeOff = new HashSet<>();

    @MappedCollection(idColumn = "employee_id")
    private Set<ServiceOffering> serviceOfferings = new HashSet<>();

    @MappedCollection(idColumn = "employee_id")
    private Set<ProductOffering> productOfferings = new HashSet<>();

    public Employee(UUID id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public Optional<ServiceOffering> serviceOffering(UUID serviceTypeId) {
        return serviceOfferings.stream().filter(o -> o.serviceTypeId().equals(serviceTypeId)).findFirst();
    }

    public Optional<ProductOffering> productOffering(UUID productId) {
        return productOfferings.stream().filter(o -> o.productId().equals(productId)).findFirst();
    }

    public void putServiceOffering(ServiceOffering offering) {
        serviceOfferings.removeIf(o -> o.serviceTypeId().equals(offering.serviceTypeId()));
        serviceOfferings.add(offering);
    }

    public void putProductOffering(ProductOffering offering) {
        productOfferings.removeIf(o -> o.productId().equals(offering.productId()));
        productOfferings.add(offering);
    }

}
