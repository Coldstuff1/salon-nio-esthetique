package ca.nio.backend.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.MappedCollection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service the salon offers (e.g. "Facial"). Employees attach their own price and duration through
 * {@link ServiceOffering}. A sub-treatment (e.g. an add-on) names one or more parents and can only be booked
 * alongside one of them; parents are never sub-treatments themselves.
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

    @JsonIgnore
    @MappedCollection(idColumn = "service_type_id")
    private Set<ServiceTypeParent> parents = new HashSet<>();

    public ServiceType(LocalizedText name, @Nullable LocalizedText description, @Nullable LocalizedText category) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /** The treatments this one can be booked with; empty for a top-level treatment. */
    @JsonProperty("parentIds")
    public List<UUID> parentIds() {
        return parents.stream().map(ServiceTypeParent::parentId).sorted().toList();
    }

    public void replaceParents(Collection<UUID> parentIds) {
        parents.clear();
        parentIds.forEach(id -> parents.add(new ServiceTypeParent(id)));
    }

    public boolean hasParents() {
        return !parents.isEmpty();
    }

}
