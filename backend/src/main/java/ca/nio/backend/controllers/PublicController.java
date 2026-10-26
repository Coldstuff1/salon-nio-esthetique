package ca.nio.backend.controllers;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.nio.backend.model.Employee;
import ca.nio.backend.model.LocalizedText;
import ca.nio.backend.model.NotificationType;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.EmployeeRepository;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;
import ca.nio.backend.services.BookingService;
import ca.nio.backend.services.ErrorCode;
import ca.nio.backend.services.MediaService;
import ca.nio.backend.services.NotificationSettingsService;
import ca.nio.backend.services.NotFoundException;
import ca.nio.backend.services.ProductSelection;
import ca.nio.backend.services.Quote;
import ca.nio.backend.services.WebPushClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private final MediaService mediaService;
    private final WebPushClient webPush;
    private final NotificationSettingsService notificationSettings;

    @GetMapping("/service-types")
    public List<ServiceType> serviceTypes() {
        return serviceTypes.findByActiveTrueOrderByNameEn();
    }

    /**
     * Each active service with its price and duration range across the active employees offering it, and who
     * they are. Ranges are null when nobody offers the service yet.
     */
    @GetMapping("/services")
    public List<ServiceSummary> services() {
        List<Employee> staff = employees.findByActiveTrueOrderByDisplayName();
        return serviceTypes.findByActiveTrueOrderByNameEn().stream()
                .map(type -> ServiceSummary.of(type, staff.stream()
                        .flatMap(e -> e.serviceOffering(type.getId())
                                .filter(ServiceOffering::active)
                                .map(o -> Map.entry(e.getId(), o))
                                .stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                                LinkedHashMap::new))))
                .toList();
    }

    /**
     * Each active product with its price range across the active employees selling it, and whether any of them
     * has it in stock. The salon's cost and SKU stay internal.
     */
    @GetMapping("/products")
    public List<ProductSummary> products() {
        List<Employee> staff = employees.findByActiveTrueOrderByDisplayName();
        return products.findByActiveTrueOrderByNameEn().stream()
                .map(product -> ProductSummary.of(product, staff.stream()
                        .flatMap(e -> e.productOffering(product.getId())
                                .filter(ProductOffering::active)
                                .map(o -> Map.entry(e.getId(), o))
                                .stream())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                                LinkedHashMap::new))))
                .toList();
    }

    @GetMapping("/employees")
    public List<EmployeeSummary> employees() {
        return employees.findByActiveTrueOrderByDisplayName().stream().map(this::summary).toList();
    }

    @GetMapping("/employees/{id}/offerings")
    public EmployeeOfferings offerings(@PathVariable UUID id) {
        Employee employee = employees.findById(id)
                .filter(Employee::isActive)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee " + id + " not found"));
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
                .sorted(Comparator.comparing((ServiceView view) -> view.name().en()))
                .toList();
        List<ProductView> productViews = employee.getProductOfferings().stream()
                .filter(ProductOffering::active)
                .filter(o -> productsById.containsKey(o.productId()) && productsById.get(o.productId()).isActive())
                .map(o -> ProductView.of(productsById.get(o.productId()), o))
                .sorted(Comparator.comparing((ProductView view) -> view.name().en()))
                .toList();
        return new EmployeeOfferings(summary(employee), services, productViews);
    }

    @GetMapping("/employees/{id}/slots")
    public List<Instant> slots(@PathVariable UUID id, @RequestParam LocalDate date,
                               @RequestParam @Size(max = 10) List<UUID> serviceIds) {
        return bookingService.freeSlots(id, date, serviceIds);
    }

    @PostMapping("/quote")
    public Quote quote(@Valid @RequestBody QuoteRequest request) {
        return bookingService.quote(request.employeeId(), request.serviceIds(), request.products());
    }

    /** The salon's VAPID public key, which browsers need to turn push notifications on. */
    @GetMapping("/push-key")
    public PushKey pushKey() {
        return new PushKey(webPush.publicKey());
    }

    /**
     * Stops the emails an unsubscribe link is about, without signing in: the site's unsubscribe page calls it,
     * and so do mail apps' own unsubscribe buttons (RFC 8058 one-click, the {@code List-Unsubscribe} header).
     */
    @PostMapping("/unsubscribe")
    public Unsubscribed unsubscribe(@RequestParam @Size(max = 200) String token) {
        return new Unsubscribed(notificationSettings.unsubscribe(token));
    }

    private EmployeeSummary summary(Employee employee) {
        return EmployeeSummary.of(employee, mediaService.url(employee.getPhotoId()));
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    public record QuoteRequest(@NotNull UUID employeeId, @NotEmpty @Size(max = 10) List<UUID> serviceIds,
                               @Size(max = 10) @Nullable List<@Valid ProductSelection> products) {
    }

    /** @param type the notifications no longer emailed */
    public record Unsubscribed(NotificationType type) {
    }

    /** @param publicKey base64url, as {@code PushManager.subscribe()} takes it for {@code applicationServerKey} */
    public record PushKey(String publicKey) {
    }

    public record EmployeeSummary(UUID id, String displayName, @Nullable String bio, @Nullable URI photoUrl) {

        static EmployeeSummary of(Employee employee, @Nullable URI photoUrl) {
            return new EmployeeSummary(employee.getId(), employee.getDisplayName(), employee.getBio(), photoUrl);
        }

    }

    public record ServiceView(UUID serviceTypeId, LocalizedText name, @Nullable LocalizedText description,
                              @Nullable LocalizedText category,
                              int priceCents, int durationMinutes) {

        static ServiceView of(ServiceType type, ServiceOffering offering) {
            return new ServiceView(type.getId(), type.getName(), type.getDescription(), type.getCategory(),
                    offering.priceCents(), offering.durationMinutes());
        }

    }

    public record ServiceSummary(UUID serviceTypeId, LocalizedText name, @Nullable LocalizedText description,
                                 @Nullable LocalizedText category,
                                 @Nullable Integer minPriceCents, @Nullable Integer maxPriceCents,
                                 @Nullable Integer minDurationMinutes, @Nullable Integer maxDurationMinutes,
                                 List<UUID> employeeIds) {

        static ServiceSummary of(ServiceType type, Map<UUID, ServiceOffering> offeringsByEmployee) {
            boolean offered = !offeringsByEmployee.isEmpty();
            IntSummaryStatistics prices = offeringsByEmployee.values().stream()
                    .mapToInt(ServiceOffering::priceCents).summaryStatistics();
            IntSummaryStatistics durations = offeringsByEmployee.values().stream()
                    .mapToInt(ServiceOffering::durationMinutes).summaryStatistics();
            return new ServiceSummary(type.getId(), type.getName(), type.getDescription(), type.getCategory(),
                    offered ? prices.getMin() : null, offered ? prices.getMax() : null,
                    offered ? durations.getMin() : null, offered ? durations.getMax() : null,
                    List.copyOf(offeringsByEmployee.keySet()));
        }

    }

    public record ProductSummary(UUID productId, LocalizedText name, @Nullable LocalizedText description,
                                 @Nullable Integer minPriceCents, @Nullable Integer maxPriceCents,
                                 boolean inStock, List<UUID> employeeIds) {

        static ProductSummary of(Product product, Map<UUID, ProductOffering> offeringsByEmployee) {
            boolean offered = !offeringsByEmployee.isEmpty();
            IntSummaryStatistics prices = offeringsByEmployee.values().stream()
                    .mapToInt(ProductOffering::priceCents).summaryStatistics();
            return new ProductSummary(product.getId(), product.getName(), product.getDescription(),
                    offered ? prices.getMin() : null, offered ? prices.getMax() : null,
                    offeringsByEmployee.values().stream().anyMatch(o -> o.quantityOnHand() > 0),
                    List.copyOf(offeringsByEmployee.keySet()));
        }

    }

    public record ProductView(UUID productId, LocalizedText name, @Nullable LocalizedText description,
                              int priceCents, int inStock) {

        static ProductView of(Product product, ProductOffering offering) {
            return new ProductView(product.getId(), product.getName(), product.getDescription(),
                    offering.priceCents(), offering.quantityOnHand());
        }

    }

    public record EmployeeOfferings(EmployeeSummary employee, List<ServiceView> services,
                                    List<ProductView> products) {
    }

}
