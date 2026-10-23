package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.ListCrudRepository;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingStatus;

public interface BookingRepository extends ListCrudRepository<Booking, UUID> {

    List<Booking> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    /** Bookings overlapping {@code [from, to)}. */
    List<Booking> findByEmployeeIdAndStartsAtBeforeAndEndsAtAfterOrderByStartsAt(UUID employeeId, Instant to,
                                                                                  Instant from);

    /** All employees' bookings overlapping {@code [from, to)}. */
    List<Booking> findByStartsAtBeforeAndEndsAtAfterOrderByStartsAt(Instant to, Instant from);

    List<Booking> findByEmployeeIdAndStatusAndStartsAtBeforeAndEndsAtAfter(UUID employeeId, BookingStatus status,
                                                                           Instant to, Instant from);

}
