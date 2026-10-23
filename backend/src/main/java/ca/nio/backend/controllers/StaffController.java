package ca.nio.backend.controllers;

import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Availability;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.TimeOff;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.services.BookingService;
import ca.nio.backend.services.CurrentUserService;
import ca.nio.backend.services.MediaService;
import ca.nio.backend.services.StaffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * An employee managing their own hours, prices, stock, and schedule.
 */
@RestController
@RequestMapping("/api/staff/me")
@PreAuthorize("hasRole('EMPLOYEE')")
@RequiredArgsConstructor
public class StaffController {

    private final CurrentUserService currentUser;
    private final StaffService staffService;
    private final BookingService bookingService;
    private final EmployeeRepository employees;
    private final MediaService mediaService;
    private final BookingViews views;

    @GetMapping
    public StaffProfile profile(@AuthenticationPrincipal Jwt jwt) {
        return profile(currentUser.employee(jwt));
    }

    @PutMapping
    public StaffProfile updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        Employee employee = currentUser.employee(jwt);
        employee.setDisplayName(request.displayName());
        employee.setBio(request.bio());
        return profile(employees.save(employee));
    }

    /** Attaches a photo uploaded through {@code POST /api/media/uploads}. */
    @PutMapping("/photo")
    public StaffProfile setPhoto(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PhotoRequest request) {
        UUID id = currentUser.employee(jwt).getId();
        return profile(staffService.setPhoto(id, id, request.mediaId()));
    }

    @DeleteMapping("/photo")
    public StaffProfile removePhoto(@AuthenticationPrincipal Jwt jwt) {
        UUID id = currentUser.employee(jwt).getId();
        return profile(staffService.setPhoto(id, id, null));
    }

    @PutMapping("/availability")
    public StaffProfile replaceAvailability(@AuthenticationPrincipal Jwt jwt,
                                            @RequestBody List<@Valid AvailabilityRequest> windows) {
        UUID id = currentUser.employee(jwt).getId();
        return profile(staffService.replaceAvailability(id, windows.stream()
                .map(w -> new Availability(w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList()));
    }

    @PostMapping("/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    public TimeOff addTimeOff(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TimeOffRequest request) {
        UUID id = currentUser.employee(jwt).getId();
        return staffService.addTimeOff(id, request.startsAt(), request.endsAt(), request.reason());
    }

    @DeleteMapping("/time-off/{timeOffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTimeOff(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID timeOffId) {
        staffService.removeTimeOff(currentUser.employee(jwt).getId(), timeOffId);
    }

    @PutMapping("/service-offerings/{serviceTypeId}")
    public StaffProfile putServiceOffering(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID serviceTypeId,
                                           @Valid @RequestBody ServiceOfferingRequest request) {
        UUID id = currentUser.employee(jwt).getId();
        return profile(staffService.putServiceOffering(id, new ServiceOffering(serviceTypeId,
                request.priceCents(), request.durationMinutes(), request.active() == null || request.active())));
    }

    @PutMapping("/product-offerings/{productId}")
    public StaffProfile putProductOffering(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId,
                                           @Valid @RequestBody ProductOfferingRequest request) {
        UUID id = currentUser.employee(jwt).getId();
        return profile(staffService.putProductOffering(id, new ProductOffering(productId,
                request.priceCents(), request.quantityOnHand(), request.active() == null || request.active())));
    }

    @GetMapping("/schedule")
    public List<BookingView> schedule(@AuthenticationPrincipal Jwt jwt, @RequestParam Instant from,
                                      @RequestParam Instant to) {
        return views.of(bookingService.schedule(CurrentUserService.userId(jwt), from, to));
    }

    private StaffProfile profile(Employee employee) {
        return StaffProfile.of(employee, mediaService.url(employee.getPhotoId()));
    }

    public record PhotoRequest(@NotNull UUID mediaId) {
    }

    public record ProfileRequest(@NotBlank @Size(max = 120) String displayName, @Nullable String bio) {
    }

    public record AvailabilityRequest(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startTime,
                                      @NotNull LocalTime endTime) {
    }

    public record TimeOffRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                 @Size(max = 255) @Nullable String reason) {
    }

    public record ServiceOfferingRequest(@Min(0) int priceCents, @Min(5) int durationMinutes,
                                         @Nullable Boolean active) {
    }

    public record ProductOfferingRequest(@Min(0) int priceCents, @Min(0) int quantityOnHand,
                                         @Nullable Boolean active) {
    }

    public record StaffProfile(UUID id, String displayName, @Nullable String bio, @Nullable URI photoUrl,
                               boolean active,
                               List<Availability> availability, List<TimeOff> timeOff,
                               Set<ServiceOffering> serviceOfferings, Set<ProductOffering> productOfferings) {

        static StaffProfile of(Employee employee, @Nullable URI photoUrl) {
            return new StaffProfile(employee.getId(), employee.getDisplayName(), employee.getBio(), photoUrl,
                    employee.isActive(),
                    employee.getAvailability().stream()
                            .sorted(Comparator.comparingInt(Availability::dayOfWeek)
                                    .thenComparing(Availability::startTime))
                            .toList(),
                    employee.getTimeOff().stream().sorted(Comparator.comparing(TimeOff::startsAt)).toList(),
                    employee.getServiceOfferings(), employee.getProductOfferings());
        }

    }

}
