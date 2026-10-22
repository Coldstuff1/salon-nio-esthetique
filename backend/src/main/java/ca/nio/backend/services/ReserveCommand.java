package ca.nio.backend.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveCommand(UUID employeeId, Instant startsAt, List<UUID> serviceIds,
                             List<ProductSelection> products, String notes) {
}
