package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

public record BookingReserved(UUID bookingId, UUID customerId, UUID employeeId, Instant startsAt) {
}
