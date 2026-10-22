package ca.nio.backend.model;

import java.util.UUID;

public record BookingCancelled(UUID bookingId, UUID customerId, UUID employeeId) {
}
