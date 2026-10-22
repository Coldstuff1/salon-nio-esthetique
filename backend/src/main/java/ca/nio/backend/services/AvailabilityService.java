package ca.nio.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import ca.nio.backend.model.Availability;
import ca.nio.backend.model.Booking;
import ca.nio.backend.model.BookingStatus;
import ca.nio.backend.model.Employee;
import ca.nio.backend.model.TimeOff;
import ca.nio.backend.repositories.BookingRepository;

import lombok.RequiredArgsConstructor;

/**
 * Works out when an employee can be booked: weekly hours, minus time off, minus existing reservations.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final BookingRepository bookings;
    private final SalonProperties salon;

    /** Start times on {@code date} where {@code durationMinutes} fits entirely within free time. */
    public List<Instant> freeSlots(Employee employee, LocalDate date, int durationMinutes) {
        ZoneId zone = salon.zone();
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Interval> busy = bookings.findByEmployeeIdAndStatusAndStartsAtBeforeAndEndsAtAfter(
                        employee.getId(), BookingStatus.RESERVED, dayEnd, dayStart).stream()
                .map(Interval::of)
                .toList();
        return computeSlots(date, zone, employee.getAvailability(), employee.getTimeOff(), busy,
                durationMinutes, salon.slotStepMinutes(), Instant.now());
    }

    /** Whether {@code [start, end)} lies inside one weekly window and doesn't touch any time off. */
    public boolean isWithinWorkingHours(Employee employee, Instant start, Instant end) {
        ZoneId zone = salon.zone();
        ZonedDateTime localStart = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return false;
        }
        LocalTime from = localStart.toLocalTime();
        LocalTime to = localEnd.toLocalTime();
        boolean inWindow = employee.getAvailability().stream()
                .filter(a -> a.day() == localStart.getDayOfWeek())
                .anyMatch(a -> !from.isBefore(a.startTime()) && !to.isAfter(a.endTime()));
        return inWindow && employee.getTimeOff().stream().noneMatch(t -> t.overlaps(start, end));
    }

    static List<Instant> computeSlots(LocalDate date, ZoneId zone, Collection<Availability> availability,
                                      Collection<TimeOff> timeOff, Collection<Interval> busy,
                                      int durationMinutes, int stepMinutes, Instant notBefore) {
        Duration duration = Duration.ofMinutes(durationMinutes);
        TreeSet<Instant> slots = new TreeSet<>();
        availability.stream()
                .filter(a -> a.day() == date.getDayOfWeek())
                .sorted(Comparator.comparing(Availability::startTime))
                .forEach(window -> {
                    int windowEnd = window.endTime().toSecondOfDay() / 60;
                    for (int minute = window.startTime().toSecondOfDay() / 60;
                         minute + durationMinutes <= windowEnd;
                         minute += stepMinutes) {
                        Instant start = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(minute * 60L), zone)
                                .toInstant();
                        Instant end = start.plus(duration);
                        if (start.isBefore(notBefore)
                                || timeOff.stream().anyMatch(t -> t.overlaps(start, end))
                                || busy.stream().anyMatch(b -> b.overlaps(start, end))) {
                            continue;
                        }
                        slots.add(start);
                    }
                });
        return List.copyOf(slots);
    }

    record Interval(Instant start, Instant end) {

        static Interval of(Booking booking) {
            return new Interval(booking.getStartsAt(), booking.getEndsAt());
        }

        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }

    }

}
