package ca.nio.backend.services;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param zone                time zone the employees' weekly hours are expressed in
 * @param slotStepMinutes     spacing between bookable start times offered to customers
 * @param maxUpcomingBookings reservations a client can hold at once, so one account can't fill the calendar
 * @param bookingHorizon      how far ahead clients can book
 * @param maxBugReportsPerDay reports one person can send in 24 hours, so one account can't flood the admins
 * @param lateCancellationNotice a client cancelling closer than this to the start time is reported to the admins
 */
@ConfigurationProperties("salon")
public record SalonProperties(@DefaultValue("America/Toronto") ZoneId zone,
                              @DefaultValue("15") int slotStepMinutes,
                              @DefaultValue("3") int maxUpcomingBookings,
                              @DefaultValue("90d") Duration bookingHorizon,
                              @DefaultValue("10") int maxBugReportsPerDay,
                              @DefaultValue("24h") Duration lateCancellationNotice) {
}
