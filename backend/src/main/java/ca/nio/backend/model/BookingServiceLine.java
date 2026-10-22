package ca.nio.backend.model;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Table;

/**
 * A service on a booking, with the name and price frozen at booking time.
 */
@Table("booking_service")
public record BookingServiceLine(UUID serviceTypeId, String nameSnapshot, int priceCents, int durationMinutes) {
}
