package ca.nio.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.Product;

public interface ProductRepository extends ListCrudRepository<Product, UUID> {

    List<Product> findByActiveTrueOrderByNameEn();

}
