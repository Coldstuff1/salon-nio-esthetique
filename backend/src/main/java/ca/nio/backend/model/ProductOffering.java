package ca.nio.backend.model;

import java.util.UUID;

/**
 * An employee's price, cost and stock for a {@link Product}: employees buy their own stock, so what a unit cost
 * them is theirs too. Stock is decremented when a booking reserves units.
 */
public record ProductOffering(UUID productId, int priceCents, int costCents, int quantityOnHand, boolean active) {
}
