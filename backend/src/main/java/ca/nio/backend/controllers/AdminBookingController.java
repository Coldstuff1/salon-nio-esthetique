package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.services.BookingService;
import lombok.RequiredArgsConstructor;

/**
 * Salon-wide view of bookings for admins. Cancelling goes through {@code POST /api/bookings/{id}/cancel}.
 */
@RestController
@RequestMapping("/api/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;
    private final BookingViews views;

    @GetMapping
    public List<BookingView> bookings(@RequestParam Instant from, @RequestParam Instant to,
                                      @RequestParam(required = false) @Nullable UUID employeeId) {
        return views.of(bookingService.allBookings(from, to, employeeId));
    }

}
