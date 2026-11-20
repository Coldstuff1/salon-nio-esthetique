package ca.nio.backend.model;

import java.util.UUID;

/** Marks a {@link ServiceType} as a sub-treatment of {@code parentId}: it can only be booked alongside a parent. */
public record ServiceTypeParent(UUID parentId) {
}
