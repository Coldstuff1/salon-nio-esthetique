package ca.nio.backend.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.ServiceType;

public interface ServiceTypeRepository extends ListCrudRepository<ServiceType, UUID> {

    List<ServiceType> findByActiveTrueOrderByName();

}
