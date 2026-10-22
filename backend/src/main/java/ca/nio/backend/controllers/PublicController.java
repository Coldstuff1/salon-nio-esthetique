package ca.nio.backend.controllers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Employee;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.services.BookingService;
import ca.nio.backend.services.NotFoundException;
import ca.nio.backend.services.ProductSelection;
import ca.nio.backend.services.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * Anonymous browsing: who works here, what they offer at what price, when they're free, and live quotes.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final EmployeeRepository employees;
    private final ServiceTypeRepository serviceTypes;
    private final ProductRepository products;
    private final BookingService bookingService;

    @GetMapping("/service-types")
    public List<ServiceType> serviceTypes() {
        return serviceTypes.findByActiveTrueOrderByName();
    }

    @GetMapping("/products")
    public List<Product> products() {
        return products.findByActiveTrueOrderByName();
    }

    @GetMapping("/employees")
    public List<EmployeeSummary> employees() {
        return employees.findByActiveTrueOrderByDisplayName().stream().map(EmployeeSummary::of).toList();
    }

    @GetMapping("/employees/{id}/offerings")
    public EmployeeOfferings offerings(@PathVariable UUID id) {
        Employee employee = employees.findById(id)
                .filter(Employee::isActive)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
        Map<UUID, ServiceType> typesById = byId(serviceTypes.findAllById(
                employee.getServiceOfferings().stream().map(ServiceOffering::serviceTypeId).toList()),
                ServiceType::getId);
        Map<UUID, Product> productsById = byId(products.findAllById(
                employee.getProductOfferings().stream().map(ProductOffering::productId).toList()),
                Product::getId);

        List<ServiceView> services = employee.getServiceOfferings().stream()
                .filter(ServiceOffering::active)
                .filter(o -> typesById.containsKey(o.serviceTypeId()) && typesById.get(o.serviceTypeId()).isActive())
                .map(o -> ServiceView.of(typesById.get(o.serviceTypeId()), o))
                .sorted(Comparator.comparing(ServiceView::name))
                .toList();
        List<ProductView> productViews = employee.getProductOfferings().stream()
                .filter(ProductOffering::active)
                .filter(o -> productsById.containsKey(o.productId()) && productsById.get(o.productId()).isActive())
                .map(o -> ProductView.of(productsById.get(o.productId()), o))
                .sorted(Comparator.comparing(ProductView::name))
                .toList();
        return new EmployeeOfferings(EmployeeSummary.of(employee), services, productViews);
    }

    @GetMapping("/employees/{id}/slots")
    public List<Instant> slots(@PathVariable UUID id, @RequestParam LocalDate date,
                               @RequestParam List<UUID> serviceIds) {
        return bookingService.freeSlots(id, date, serviceIds);
    }

    @PostMapping("/quote")
    public Quote quote(@Valid @RequestBody QuoteRequest request) {
        return bookingService.quote(request.employeeId(), request.serviceIds(), request.products());
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    public record QuoteRequest(@NotNull UUID employeeId, @NotEmpty List<UUID> serviceIds,
                               List<@Valid ProductSelection> products) {
    }

    public record EmployeeSummary(UUID id, String displayName, String bio) {

        static EmployeeSummary of(Employee employee) {
            return new EmployeeSummary(employee.getId(), employee.getDisplayName(), employee.getBio());
        }

    }

    public record ServiceView(UUID serviceTypeId, String name, String description, String category,
                              int priceCents, int durationMinutes) {

        static ServiceView of(ServiceType type, ServiceOffering offering) {
            return new ServiceView(type.getId(), type.getName(), type.getDescription(), type.getCategory(),
                    offering.priceCents(), offering.durationMinutes());
        }

    }

    public record ProductView(UUID productId, String name, String description, int priceCents, int inStock) {

        static ProductView of(Product product, ProductOffering offering) {
            return new ProductView(product.getId(), product.getName(), product.getDescription(),
                    offering.priceCents(), offering.quantityOnHand());
        }

    }

    public record EmployeeOfferings(EmployeeSummary employee, List<ServiceView> services,
                                    List<ProductView> products) {
    }

}
