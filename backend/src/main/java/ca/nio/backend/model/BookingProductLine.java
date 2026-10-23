package ca.nio.backend.model;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A product reserved on a booking, with the name and unit price frozen at booking time.
 */
@Table("booking_product")
public record BookingProductLine(UUID productId,
                                 @Embedded.Nullable(prefix = "name_snapshot_") LocalizedText nameSnapshot,
                                 int unitPriceCents, int quantity) {

    public int totalCents() {
        return unitPriceCents * quantity;
    }

}
