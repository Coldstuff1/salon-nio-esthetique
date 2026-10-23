package ca.nio.backend.controllers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.BookingStatus;
import ca.nio.backend.services.BookingService;
import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.ProductSelection;
import ca.nio.backend.services.ReserveCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserService currentUser;
    private final BookingViews views;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public BookingView reserve(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
        CurrentUserService.requireVerifiedEmail(jwt);
        UUID customerId = currentUser.customer(jwt).getId();
        return views.of(bookingService.reserve(customerId, new ReserveCommand(request.employeeId(),
                request.startsAt(), request.serviceIds(), request.products(), request.notes())));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<BookingView> mine(@AuthenticationPrincipal Jwt jwt) {
        return views.of(bookingService.customerBookings(CurrentUserService.userId(jwt)));
    }

    @PostMapping("/{id}/cancel")
    public BookingView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return views.of(bookingService.cancel(id, CurrentUserService.userId(jwt), admin));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public BookingView complete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return views.of(bookingService.close(id, currentUser.employee(jwt).getId(), BookingStatus.COMPLETED));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public BookingView noShow(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return views.of(bookingService.close(id, currentUser.employee(jwt).getId(), BookingStatus.NO_SHOW));
    }

    public record BookingRequest(@NotNull UUID employeeId, @NotNull Instant startsAt,
                                 @NotEmpty @Size(max = 10) List<UUID> serviceIds,
                                 @Size(max = 10) @Nullable List<@Valid ProductSelection> products,
                                 @Size(max = 1000) @Nullable String notes) {
    }

}
