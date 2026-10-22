package ca.nio.backend.services;

import java.util.List;
import java.util.UUID;

import ca.nio.backend.model.BookingProductLine;
import ca.nio.backend.model.BookingServiceLine;

/**
 * Priced selection for one employee. The same lines become the booking's line items.
 */
public record Quote(UUID employeeId, List<BookingServiceLine> services, List<BookingProductLine> products,
                    int subtotalCents, int totalDurationMinutes) {
}
