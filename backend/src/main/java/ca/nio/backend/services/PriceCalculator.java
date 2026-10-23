package ca.nio.backend.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ca.nio.backend.model.BookingProductLine;
import ca.nio.backend.model.BookingServiceLine;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;

import lombok.RequiredArgsConstructor;

/**
 * Prices a selection of services and products against one employee's current offerings.
 */
@Service
@RequiredArgsConstructor
public class PriceCalculator {

    private final ServiceTypeRepository serviceTypes;
    private final ProductRepository products;

    public Quote quote(Employee employee, List<UUID> serviceIds, List<ProductSelection> productSelections) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new InvalidRequestException(ErrorCode.NO_SERVICE_SELECTED, "Select at least one service");
        }
        if (new HashSet<>(serviceIds).size() != serviceIds.size()) {
            throw new InvalidRequestException(ErrorCode.DUPLICATE_SERVICE, "A service can only be selected once");
        }
        List<ProductSelection> selections = productSelections == null ? List.of() : productSelections;
        if (selections.stream().map(ProductSelection::productId).distinct().count() != selections.size()) {
            throw new InvalidRequestException(ErrorCode.DUPLICATE_PRODUCT,
                    "A product can only be listed once; use the quantity instead");
        }

        List<BookingServiceLine> serviceLines = new ArrayList<>();
        for (UUID serviceId : serviceIds) {
            ServiceOffering offering = employee.serviceOffering(serviceId)
                    .filter(ServiceOffering::active)
                    .orElseThrow(() -> new InvalidRequestException(ErrorCode.SERVICE_NOT_OFFERED,
                            "Employee doesn't offer service " + serviceId));
            ServiceType type = serviceTypes.findById(serviceId)
                    .filter(ServiceType::isActive)
                    .orElseThrow(() -> new InvalidRequestException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Service " + serviceId + " is not available"));
            serviceLines.add(new BookingServiceLine(serviceId, type.getName(), offering.priceCents(),
                    offering.durationMinutes()));
        }

        List<BookingProductLine> productLines = new ArrayList<>();
        for (ProductSelection selection : selections) {
            if (selection.quantity() < 1) {
                throw new InvalidRequestException(ErrorCode.INVALID_QUANTITY, "Quantity must be at least 1");
            }
            ProductOffering offering = employee.productOffering(selection.productId())
                    .filter(ProductOffering::active)
                    .orElseThrow(() -> new InvalidRequestException(ErrorCode.PRODUCT_NOT_OFFERED,
                            "Employee doesn't sell product " + selection.productId()));
            Product product = products.findById(selection.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new InvalidRequestException(ErrorCode.PRODUCT_UNAVAILABLE,
                            "Product " + selection.productId() + " is not available"));
            if (selection.quantity() > offering.quantityOnHand()) {
                throw new ConflictException(ErrorCode.OUT_OF_STOCK, "Only " + offering.quantityOnHand() + " of "
                        + product.getName().en() + " left in stock",
                        Map.of("product", product.getName().en(), "available", offering.quantityOnHand()));
            }
            productLines.add(new BookingProductLine(product.getId(), product.getName(), offering.priceCents(),
                    selection.quantity()));
        }

        int subtotal = serviceLines.stream().mapToInt(BookingServiceLine::priceCents).sum()
                + productLines.stream().mapToInt(BookingProductLine::totalCents).sum();
        int duration = serviceLines.stream().mapToInt(BookingServiceLine::durationMinutes).sum();
        return new Quote(employee.getId(), serviceLines, productLines, subtotal, duration);
    }

}
