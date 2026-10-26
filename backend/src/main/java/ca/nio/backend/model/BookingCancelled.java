package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

/** @param cancelledBy the client, the employee or an admin (Keycloak subject) */
public record BookingCancelled(UUID bookingId, UUID customerId, UUID employeeId, Instant startsAt, UUID cancelledBy) {
}
