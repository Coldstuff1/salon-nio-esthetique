package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingProductLine;
import ca.nio.backend.model.BookingServiceLine;
import ca.nio.backend.model.BookingStatus;
import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;

/**
 * A booking with the names and contact details needed to display it, so clients see who they're booked with
 * and staff see who to expect. Built by {@link BookingViews}.
 */
public record BookingView(UUID id, UUID customerId, String customerName, @Nullable String customerEmail,
                          @Nullable String customerPhone, UUID employeeId, String employeeName,
                          Instant startsAt, Instant endsAt,
                          BookingStatus status, int subtotalCents, @Nullable String notes,
                          List<BookingServiceLine> services, List<BookingProductLine> products) {

    static BookingView of(Booking booking, Customer customer, Employee employee) {
        return new BookingView(booking.getId(), booking.getCustomerId(), customer.getDisplayName(),
                customer.getEmail(), customer.getPhone(), booking.getEmployeeId(), employee.getDisplayName(),
                booking.getStartsAt(), booking.getEndsAt(), booking.getStatus(), booking.getSubtotalCents(),
                booking.getNotes(), List.copyOf(booking.getServices()),
                booking.getProducts().stream().sorted(Comparator.comparing(BookingProductLine::nameSnapshot)).toList());
    }

}
