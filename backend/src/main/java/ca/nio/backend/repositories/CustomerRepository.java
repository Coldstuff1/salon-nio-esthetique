package ca.nio.backend.repositories;

import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.Customer;

public interface CustomerRepository extends ListCrudRepository<Customer, UUID> {
}
