package ca.nio.backend.model;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An employee's price and duration for a {@link ServiceType}, and where it sits in their list: numbered offerings
 * come first, lowest number first; unnumbered ones ({@code displayOrder} null) follow, alphabetically. A
 * {@code dailyLimit} caps how many bookings of the treatment the employee takes per day; null means no cap.
 */
public record ServiceOffering(UUID serviceTypeId, int priceCents, int durationMinutes, boolean active,
                              @Nullable Integer displayOrder, @Nullable Integer dailyLimit) {
}
