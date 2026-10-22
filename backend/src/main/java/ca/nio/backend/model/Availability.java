package ca.nio.backend.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * A recurring weekly working window, in the salon's time zone. {@code dayOfWeek} is ISO (1 = Monday).
 */
public record Availability(int dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public Availability {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
    }

    public static Availability of(DayOfWeek day, LocalTime startTime, LocalTime endTime) {
        return new Availability(day.getValue(), startTime, endTime);
    }

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

}
