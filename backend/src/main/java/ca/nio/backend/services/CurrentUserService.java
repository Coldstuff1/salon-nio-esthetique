package ca.nio.backend.services;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.EmployeeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Maps the Keycloak token to local profiles, creating them on first use.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final CustomerRepository customers;
    private final EmployeeRepository employees;

    public static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
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

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        return name != null && !name.isBlank() ? name : jwt.getClaimAsString("preferred_username");
    }

}
