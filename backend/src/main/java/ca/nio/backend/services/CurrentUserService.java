package ca.nio.backend.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.NotificationRecipientRepository;

import lombok.RequiredArgsConstructor;

/**
 * Maps the Keycloak token to local profiles, creating them on first use.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /** Keycloak also hands out {@code default-roles-nio}, {@code offline_access} and the like; these are ours. */
    private static final Set<String> APP_ROLES = Set.of("client", "employee", "admin");

    private final CustomerRepository customers;
    private final EmployeeRepository employees;
    private final NotificationRecipientRepository recipients;

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Every realm role in the token ({@code realm_access.roles}). */
    public static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }

    /** The realm roles this app knows about: client, employee and admin. */
    public static List<String> appRoles(Jwt jwt) {
        return realmRoles(jwt).stream().filter(APP_ROLES::contains).sorted().toList();
    }

    /** Keycloak asks new accounts to verify their email; this also covers accounts made before it did. */
    public static void requireVerifiedEmail(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email address first");
        }
    }

    public Customer customer(Jwt jwt) {
        return customers.findById(userId(jwt))
                .orElseGet(() -> customers.save(new Customer(userId(jwt), displayName(jwt),
                        jwt.getClaimAsString("email"))));
    }

    /** Copies how to reach this person (email, name, roles, language) for notifications. */
    @Transactional
    public void syncRecipient(Jwt jwt) {
        String locale = jwt.getClaimAsString("locale");
        recipients.upsert(userId(jwt), jwt.getClaimAsString("email"), displayName(jwt), String.join(",", appRoles(jwt)),
                locale == null ? null : locale.startsWith("en") ? "en" : locale.startsWith("fr") ? "fr" : null);
    }

    /**
     * Callers must already have checked the token carries the employee role. An employee an admin deactivated
     * keeps their Keycloak role until someone removes it, so they are refused here.
     */
    public Employee employee(Jwt jwt) {
        Employee employee = employees.findById(userId(jwt))
                .orElseGet(() -> employees.save(new Employee(userId(jwt), displayName(jwt))));
        if (!employee.isActive()) {
            throw new ForbiddenException(ErrorCode.EMPLOYEE_INACTIVE, "Employee " + employee.getId() + " is deactivated");
        }
        return employee;
    }

    /** The person's full name, or their username when Keycloak has none. */
    public static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        return name != null && !name.isBlank() ? name : jwt.getClaimAsString("preferred_username");
    }

}
