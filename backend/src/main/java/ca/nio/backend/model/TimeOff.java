package ca.nio.backend.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-off block where the employee can't be booked (vacation, sick day...).
 */
public record TimeOff(UUID id, Instant startsAt, Instant endsAt, String reason) {

    public TimeOff {
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
    }

    public boolean overlaps(Instant start, Instant end) {
        return startsAt.isBefore(end) && start.isBefore(endsAt);
    }

}
