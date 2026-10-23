package ca.nio.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingCancelled;
import ca.nio.backend.model.BookingProductLine;
import ca.nio.backend.model.BookingReserved;
import ca.nio.backend.model.BookingStatus;
import ca.nio.backend.model.Employee;
import ca.nio.backend.repositories.BookingRepository;
import ca.nio.backend.repositories.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookings;
    private final EmployeeRepository employees;
    private final PriceCalculator priceCalculator;
    private final AvailabilityService availability;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Quote quote(UUID employeeId, List<UUID> serviceIds, List<ProductSelection> products) {
        return priceCalculator.quote(activeEmployee(employeeId), serviceIds, products);
    }

    @Transactional(readOnly = true)
    public List<Instant> freeSlots(UUID employeeId, LocalDate date, List<UUID> serviceIds) {
        Employee employee = activeEmployee(employeeId);
        Quote quote = priceCalculator.quote(employee, serviceIds, List.of());
        return availability.freeSlots(employee, date, quote.totalDurationMinutes());
    }

    /**
     * Books the slot and holds product stock. The {@code booking_no_overlap} constraint is the final guard
     * against two concurrent reservations of the same time.
     */
    @Transactional
    public Booking reserve(UUID customerId, ReserveCommand command) {
        Employee employee = activeEmployee(command.employeeId());
        if (command.startsAt() == null || !command.startsAt().isAfter(Instant.now())) {
            throw new InvalidRequestException(ErrorCode.START_IN_PAST, "Start time must be in the future");
        }
        Quote quote = priceCalculator.quote(employee, command.serviceIds(), command.products());
        Instant startsAt = command.startsAt();
        Instant endsAt = startsAt.plus(Duration.ofMinutes(quote.totalDurationMinutes()));

        if (!availability.isWithinWorkingHours(employee, startsAt, endsAt)) {
            throw new ConflictException(ErrorCode.OUTSIDE_WORKING_HOURS, "Employee isn't working at that time");
        }
        if (!bookings.findByEmployeeIdAndStatusAndStartsAtBeforeAndEndsAtAfter(
                employee.getId(), BookingStatus.RESERVED, endsAt, startsAt).isEmpty()) {
            throw new ConflictException(ErrorCode.SLOT_TAKEN, "That time slot is already booked");
        }
        for (BookingProductLine line : quote.products()) {
            if (employees.reserveStock(employee.getId(), line.productId(), line.quantity()) == 0) {
                throw new ConflictException(ErrorCode.OUT_OF_STOCK, "Not enough stock for " + line.nameSnapshot().en(),
                        Map.of("product", line.nameSnapshot().en()));
            }
        }
        if (!quote.products().isEmpty()) {
            employees.touch(employee.getId());
        }

        Booking booking = bookings.save(new Booking(customerId, employee.getId(), startsAt, endsAt,
                quote.services(), new HashSet<>(quote.products()), command.notes()));
        events.publishEvent(new BookingReserved(booking.getId(), customerId, employee.getId(), startsAt));
        return booking;
    }

    /**
     * Cancels a reserved booking and returns its products to stock. Customers can cancel their own bookings
     * before they start; the assigned employee and admins can cancel any time.
     */
    @Transactional
    public Booking cancel(UUID bookingId, UUID actorId, boolean admin) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> admin || b.getEmployeeId().equals(actorId) || b.getCustomerId().equals(actorId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + bookingId + " not found"));
        boolean byCustomer = !admin && booking.getCustomerId().equals(actorId)
                && !booking.getEmployeeId().equals(actorId);
        if (byCustomer && !booking.getStartsAt().isAfter(Instant.now())) {
            throw new ConflictException(ErrorCode.CANCEL_AFTER_START, "Bookings can't be cancelled after they start");
        }
        transition(booking, BookingStatus.CANCELLED);
        booking.getProducts().forEach(line ->
                employees.releaseStock(booking.getEmployeeId(), line.productId(), line.quantity()));
        if (!booking.getProducts().isEmpty()) {
            employees.touch(booking.getEmployeeId());
        }
        Booking saved = bookings.save(booking);
        events.publishEvent(new BookingCancelled(saved.getId(), saved.getCustomerId(), saved.getEmployeeId()));
        return saved;
    }

    /** Marks the employee's own booking as completed or no-show. */
    @Transactional
    public Booking close(UUID bookingId, UUID employeeId, BookingStatus outcome) {
        if (outcome != BookingStatus.COMPLETED && outcome != BookingStatus.NO_SHOW) {
            throw new InvalidRequestException(ErrorCode.INVALID_OUTCOME, "Outcome must be COMPLETED or NO_SHOW");
        }
        Booking booking = bookings.findById(bookingId)
                .filter(b -> b.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new NotFoundException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + bookingId + " not found"));
        transition(booking, outcome);
        return bookings.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> customerBookings(UUID customerId) {
        return bookings.findByCustomerIdOrderByStartsAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Booking> schedule(UUID employeeId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidRequestException(ErrorCode.INVALID_RANGE, "'from' must be before 'to'");
        }
        return bookings.findByEmployeeIdAndStartsAtBeforeAndEndsAtAfterOrderByStartsAt(employeeId, to, from);
    }

    /** Every booking overlapping {@code [from, to)}, optionally for one employee. */
    @Transactional(readOnly = true)
    public List<Booking> allBookings(Instant from, Instant to, @Nullable UUID employeeId) {
        if (employeeId != null) {
            return schedule(employeeId, from, to);
        }
        if (!from.isBefore(to)) {
            throw new InvalidRequestException(ErrorCode.INVALID_RANGE, "'from' must be before 'to'");
        }
        return bookings.findByStartsAtBeforeAndEndsAtAfterOrderByStartsAt(to, from);
    }

    private Employee activeEmployee(UUID employeeId) {
        return employees.findById(employeeId)
                .filter(Employee::isActive)
                .orElseThrow(() -> new NotFoundException(ErrorCode.EMPLOYEE_NOT_FOUND, "Employee " + employeeId + " not found"));
    }

    private static void transition(Booking booking, BookingStatus next) {
        try {
            booking.transitionTo(next);
        } catch (IllegalStateException e) {
            throw new ConflictException(ErrorCode.BOOKING_CLOSED, e.getMessage());
        }
    }

}
