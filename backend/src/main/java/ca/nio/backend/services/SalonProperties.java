package ca.nio.backend.services;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param zone            time zone the employees' weekly hours are expressed in
 * @param slotStepMinutes spacing between bookable start times offered to customers
 */
@ConfigurationProperties("salon")
public record SalonProperties(@DefaultValue("America/Toronto") ZoneId zone,
                              @DefaultValue("15") int slotStepMinutes) {
}
