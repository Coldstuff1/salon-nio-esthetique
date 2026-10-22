package ca.nio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ca.nio.backend.model.Employee;
import ca.nio.backend.model.Product;
import ca.nio.backend.model.ProductOffering;
import ca.nio.backend.model.ServiceOffering;
import ca.nio.backend.model.ServiceType;
import ca.nio.backend.repositories.ProductRepository;
import ca.nio.backend.repositories.ServiceTypeRepository;

class PriceCalculatorTest {

    ServiceTypeRepository serviceTypes = mock(ServiceTypeRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    PriceCalculator calculator = new PriceCalculator(serviceTypes, products);

    ServiceType facial = new ServiceType("Facial", null, "face");
    ServiceType manicure = new ServiceType("Manicure", null, "hands");
    Product cream = new Product("Cream", null, "CR-1");
    Employee employee = new Employee(UUID.randomUUID(), "Nio");

    @BeforeEach
    void setUp() {
        when(serviceTypes.findById(facial.getId())).thenReturn(Optional.of(facial));
        when(serviceTypes.findById(manicure.getId())).thenReturn(Optional.of(manicure));
        when(products.findById(cream.getId())).thenReturn(Optional.of(cream));
        employee.putServiceOffering(new ServiceOffering(facial.getId(), 8000, 60, true));
        employee.putServiceOffering(new ServiceOffering(manicure.getId(), 4500, 30, true));
        employee.putProductOffering(new ProductOffering(cream.getId(), 2500, 3, true));
    }

    @Test
    void sumsServicesAndProductsAtTheEmployeesPrices() {
        Quote quote = calculator.quote(employee, List.of(facial.getId(), manicure.getId()),
                List.of(new ProductSelection(cream.getId(), 2)));

        assertThat(quote.subtotalCents()).isEqualTo(8000 + 4500 + 2 * 2500);
        assertThat(quote.totalDurationMinutes()).isEqualTo(90);
        assertThat(quote.services()).extracting("nameSnapshot").containsExactly("Facial", "Manicure");
        assertThat(quote.products()).singleElement().satisfies(line -> {
            assertThat(line.unitPriceCents()).isEqualTo(2500);
            assertThat(line.quantity()).isEqualTo(2);
        });
    }

    @Test
    void rejectsInactiveOffering() {
        employee.putServiceOffering(new ServiceOffering(manicure.getId(), 4500, 30, false));

        assertThatThrownBy(() -> calculator.quote(employee, List.of(manicure.getId()), List.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsServiceTheEmployeeDoesNotOffer() {
        assertThatThrownBy(() -> calculator.quote(employee, List.of(UUID.randomUUID()), List.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsMoreProductsThanInStock() {
        assertThatThrownBy(() -> calculator.quote(employee, List.of(facial.getId()),
                List.of(new ProductSelection(cream.getId(), 4))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requiresAtLeastOneService() {
        assertThatThrownBy(() -> calculator.quote(employee, List.of(), List.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

}
