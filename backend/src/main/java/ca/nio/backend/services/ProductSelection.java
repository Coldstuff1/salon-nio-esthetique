package ca.nio.backend.services;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductSelection(@NotNull UUID productId, @Min(1) @Max(10) int quantity) {
}
