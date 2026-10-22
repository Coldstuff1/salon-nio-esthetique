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

    public Customer customer(Jwt jwt) {
        return customers.findById(userId(jwt))
                .orElseGet(() -> customers.save(new Customer(userId(jwt), displayName(jwt),
                        jwt.getClaimAsString("email"))));
    }

    /** Callers must already have checked the token carries the employee role. */
    public Employee employee(Jwt jwt) {
        return employees.findById(userId(jwt))
                .orElseGet(() -> employees.save(new Employee(userId(jwt), displayName(jwt))));
    }

    private static String displayName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        return name != null && !name.isBlank() ? name : jwt.getClaimAsString("preferred_username");
    }

}
