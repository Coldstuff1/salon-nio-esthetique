package ca.nio.backend.controllers;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Customer;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.services.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * A client's contact details as the salon sees them. The email is copied from Keycloak and changed there.
 */
@RestController
@RequestMapping("/api/me/profile")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CurrentUserService currentUser;
    private final CustomerRepository customers;

    @GetMapping
    public CustomerProfile profile(@AuthenticationPrincipal Jwt jwt) {
        return CustomerProfile.of(currentUser.customer(jwt));
    }

    @PutMapping
    public CustomerProfile updateProfile(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody CustomerProfileRequest request) {
        Customer customer = currentUser.customer(jwt);
        customer.setDisplayName(request.displayName());
        customer.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());
        return CustomerProfile.of(customers.save(customer));
    }

    public record CustomerProfileRequest(@NotBlank @Size(max = 120) String displayName,
                                         @Size(max = 30) @Pattern(regexp = "[0-9+()\\-. ]*") @Nullable String phone) {
    }

    public record CustomerProfile(UUID id, String displayName, @Nullable String email, @Nullable String phone) {

        static CustomerProfile of(Customer customer) {
            return new CustomerProfile(customer.getId(), customer.getDisplayName(), customer.getEmail(),
                    customer.getPhone());
        }

    }

}
