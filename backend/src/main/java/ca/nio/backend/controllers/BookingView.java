package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingProductLine;
import ca.nio.backend.model.BookingServiceLine;
import ca.nio.backend.model.BookingStatus;

public record BookingView(UUID id, UUID customerId, UUID employeeId, Instant startsAt, Instant endsAt,
                          BookingStatus status, int subtotalCents, String notes,
                          List<BookingServiceLine> services, List<BookingProductLine> products) {

    static BookingView of(Booking booking) {
        return new BookingView(booking.getId(), booking.getCustomerId(), booking.getEmployeeId(),
                booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(), booking.getSubtotalCents(),
                booking.getNotes(), List.copyOf(booking.getServices()),
                booking.getProducts().stream().sorted(Comparator.comparing(BookingProductLine::nameSnapshot)).toList());
    }

}
