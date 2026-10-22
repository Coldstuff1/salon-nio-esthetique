package ca.nio.backend.controllers;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Employee;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.services.ConflictException;
import ca.nio.backend.services.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Admin management of the shared catalog and employee records. Deleting only deactivates, since past
 * bookings and offerings still point at the row.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CatalogAdminController {

    private final ServiceTypeRepository serviceTypes;
    private final ProductRepository products;
    private final EmployeeRepository employees;

    @GetMapping("/service-types")
    public List<ServiceType> serviceTypes() {
        return serviceTypes.findAll();
    }

    @PostMapping("/service-types")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceType createServiceType(@Valid @RequestBody ServiceTypeRequest request) {
        return serviceTypes.save(new ServiceType(request.name(), request.description(), request.category()));
    }

    @PutMapping("/service-types/{id}")
    public ServiceType updateServiceType(@PathVariable UUID id, @Valid @RequestBody ServiceTypeRequest request) {
        ServiceType type = serviceType(id);
        type.setName(request.name());
        type.setDescription(request.description());
        type.setCategory(request.category());
        type.setActive(request.active() == null || request.active());
        return serviceTypes.save(type);
    }

    @DeleteMapping("/service-types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateServiceType(@PathVariable UUID id) {
        ServiceType type = serviceType(id);
        type.setActive(false);
        serviceTypes.save(type);
    }

    @GetMapping("/products")
    public List<Product> products() {
        return products.findAll();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        return products.save(new Product(request.name(), request.description(), request.sku()));
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        Product product = product(id);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setSku(request.sku());
        product.setActive(request.active() == null || request.active());
        return products.save(product);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateProduct(@PathVariable UUID id) {
        Product product = product(id);
        product.setActive(false);
        products.save(product);
    }

    /** Registers a Keycloak user (by subject id) as an employee ahead of their first login. */
    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicController.EmployeeSummary createEmployee(@Valid @RequestBody EmployeeRequest request) {
        if (employees.existsById(request.id())) {
            throw new ConflictException("Employee " + request.id() + " already exists");
        }
        Employee employee = new Employee(request.id(), request.displayName());
        employee.setBio(request.bio());
        return PublicController.EmployeeSummary.of(employees.save(employee));
    }

    @DeleteMapping("/employees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateEmployee(@PathVariable UUID id) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
        employee.setActive(false);
        employees.save(employee);
    }

    private ServiceType serviceType(UUID id) {
        return serviceTypes.findById(id).orElseThrow(() -> new NotFoundException("Service " + id + " not found"));
    }

    private Product product(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    public record ServiceTypeRequest(@NotBlank @Size(max = 120) String name, @Nullable String description,
                                     @Size(max = 60) @Nullable String category, @Nullable Boolean active) {
    }

    public record ProductRequest(@NotBlank @Size(max = 120) String name, @Nullable String description,
                                 @NotBlank @Size(max = 60) String sku, @Nullable Boolean active) {
    }

    public record EmployeeRequest(@NotNull UUID id, @NotBlank @Size(max = 120) String displayName, @Nullable String bio) {
    }

}
