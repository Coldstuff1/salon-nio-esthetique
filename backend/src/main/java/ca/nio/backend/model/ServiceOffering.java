package ca.nio.backend.model;

import java.util.UUID;

/**
 * An employee's price and duration for a {@link ServiceType}.
 */
public record ServiceOffering(UUID serviceTypeId, int priceCents, int durationMinutes, boolean active) {
}
