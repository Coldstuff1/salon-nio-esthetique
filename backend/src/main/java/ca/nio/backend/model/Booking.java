package ca.nio.backend.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A reservation of one employee's time for one or more back-to-back services, plus products to buy on site.
 */
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    private UUID id;
    private UUID customerId;
    private UUID employeeId;
    private Instant startsAt;
    private Instant endsAt;
    private BookingStatus status;
    private int subtotalCents;
    private String notes;
    private Instant createdAt;
    @Version
    private Integer version;

    @MappedCollection(idColumn = "booking_id", keyColumn = "position")
    private List<BookingServiceLine> services = new ArrayList<>();

    @MappedCollection(idColumn = "booking_id")
    private Set<BookingProductLine> products = new HashSet<>();

    public Booking(UUID customerId, UUID employeeId, Instant startsAt, Instant endsAt,
                   List<BookingServiceLine> services, Set<BookingProductLine> products, String notes) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.employeeId = employeeId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = BookingStatus.RESERVED;
        this.services = new ArrayList<>(services);
        this.products = new HashSet<>(products);
        this.subtotalCents = services.stream().mapToInt(BookingServiceLine::priceCents).sum()
                + products.stream().mapToInt(BookingProductLine::totalCents).sum();
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public void transitionTo(BookingStatus next) {
        if (status != BookingStatus.RESERVED) {
            throw new IllegalStateException("Booking is already " + status);
        }
        status = next;
    }

}
