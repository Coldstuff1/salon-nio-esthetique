package ca.nio.backend.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingStatus;

public interface BookingRepository extends ListCrudRepository<Booking, UUID> {

    List<Booking> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    long countByCustomerIdAndStatusAndStartsAtAfter(UUID customerId, BookingStatus status, Instant after);

    /** Bookings overlapping {@code [from, to)}. */
    List<Booking> findByEmployeeIdAndStartsAtBeforeAndEndsAtAfterOrderByStartsAt(UUID employeeId, Instant to,
                                                                                  Instant from);

    /** All employees' bookings overlapping {@code [from, to)}. */
    List<Booking> findByStartsAtBeforeAndEndsAtAfterOrderByStartsAt(Instant to, Instant from);

    /** Bookings in a status starting in {@code [from, to)}. */
    List<Booking> findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(BookingStatus status, Instant from,
                                                                              Instant to);

    /** Bookings in a status ending in {@code [from, to)}. */
    List<Booking> findByStatusAndEndsAtGreaterThanEqualAndEndsAtLessThan(BookingStatus status, Instant from,
                                                                          Instant to);

    List<Booking> findByEmployeeIdAndStatusAndStartsAtBeforeAndEndsAtAfter(UUID employeeId, BookingStatus status,
                                                                           Instant to, Instant from);

    /** An employee's bookings of a treatment that start in {@code [from, to)} and weren't cancelled. */
    @Query("""
            SELECT COUNT(*) FROM booking b JOIN booking_service s ON s.booking_id = b.id
            WHERE b.employee_id = :employeeId AND s.service_type_id = :serviceTypeId AND b.status <> 'CANCELLED'
              AND b.starts_at >= :from AND b.starts_at < :to
            """)
    long countUncancelledWithServiceStartingBetween(@Param("employeeId") UUID employeeId,
                                                    @Param("serviceTypeId") UUID serviceTypeId,
                                                    @Param("from") Instant from, @Param("to") Instant to);

}
