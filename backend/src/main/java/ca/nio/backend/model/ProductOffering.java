package ca.nio.backend.model;

import java.util.UUID;

/**
 * An employee's price and stock for a {@link Product}. Stock is decremented when a booking reserves units.
 */
public record ProductOffering(UUID productId, int priceCents, int quantityOnHand, boolean active) {
}
