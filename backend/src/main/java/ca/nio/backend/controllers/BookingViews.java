package ca.nio.backend.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.Customer;
import ca.nio.backend.model.Employee;
import ca.nio.backend.repositories.CustomerRepository;
import ca.nio.backend.repositories.EmployeeRepository;
import lombok.RequiredArgsConstructor;

/**
 * Turns bookings into {@link BookingView}s, loading the customers and employees they refer to in one query each.
 */
@Component
@RequiredArgsConstructor
class BookingViews {

    private final CustomerRepository customers;
    private final EmployeeRepository employees;

    BookingView of(Booking booking) {
        return of(List.of(booking)).getFirst();
    }

    List<BookingView> of(List<Booking> bookings) {
        Map<UUID, Customer> customersById = byId(customers.findAllById(
                bookings.stream().map(Booking::getCustomerId).distinct().toList()), Customer::getId);
        Map<UUID, Employee> employeesById = byId(employees.findAllById(
                bookings.stream().map(Booking::getEmployeeId).distinct().toList()), Employee::getId);
        return bookings.stream()
                .map(b -> BookingView.of(b, customersById.get(b.getCustomerId()), employeesById.get(b.getEmployeeId())))
                .toList();
    }

    private static <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

}
