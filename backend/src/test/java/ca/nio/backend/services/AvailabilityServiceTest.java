package ca.nio.backend.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ca.nio.backend.model.Availability;
import ca.nio.backend.model.TimeOff;
import ca.nio.backend.services.AvailabilityService.Interval;

class AvailabilityServiceTest {

    static final ZoneId ZONE = ZoneId.of("America/Toronto");
    static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);
    static final Instant LONG_AGO = Instant.EPOCH;

    @Test
    void offersSlotsAcrossSplitShiftWhereDurationFits() {
        List<Availability> hours = List.of(
                Availability.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                Availability.of(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(13, 45)),
                Availability.of(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

        List<Instant> slots = AvailabilityService.computeSlots(MONDAY, ZONE, hours, List.of(), List.of(),
                30, 15, LONG_AGO);

        assertThat(slots).containsExactly(at(9, 0), at(9, 15), at(9, 30), at(13, 0), at(13, 15));
    }

    @Test
    void skipsTimeOffAndExistingBookings() {
        List<Availability> hours = List.of(
                Availability.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        List<TimeOff> timeOff = List.of(new TimeOff(UUID.randomUUID(), at(9, 0), at(10, 0), "dentist"));
        List<Interval> busy = List.of(new Interval(at(10, 30), at(11, 30)));

        List<Instant> slots = AvailabilityService.computeSlots(MONDAY, ZONE, hours, timeOff, busy,
                30, 30, LONG_AGO);

        assertThat(slots).containsExactly(at(10, 0), at(11, 30));
    }

    @Test
    void hidesSlotsInThePast() {
        List<Availability> hours = List.of(
                Availability.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        List<Instant> slots = AvailabilityService.computeSlots(MONDAY, ZONE, hours, List.of(), List.of(),
                60, 60, at(9, 30));

        assertThat(slots).containsExactly(at(10, 0));
    }

    @Test
    void returnsNothingOnADayOff() {
        List<Availability> hours = List.of(
                Availability.of(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));

        assertThat(AvailabilityService.computeSlots(MONDAY, ZONE, hours, List.of(), List.of(), 30, 15, LONG_AGO))
                .isEmpty();
    }

    private static Instant at(int hour, int minute) {
        return ZonedDateTime.of(MONDAY, LocalTime.of(hour, minute), ZONE).toInstant();
    }

}
