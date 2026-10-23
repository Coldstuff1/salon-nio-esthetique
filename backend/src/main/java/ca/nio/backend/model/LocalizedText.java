package ca.nio.backend.model;

import org.jspecify.annotations.Nullable;

/**
 * Catalog text in both languages the salon serves. English is always present; French is optional and readers
 * fall back to the English when it's missing.
 */
public record LocalizedText(String en, @Nullable String fr) {

    /** Trims both values and drops a blank French one. */
    public static LocalizedText of(String en, @Nullable String fr) {
        return new LocalizedText(en.trim(), fr == null || fr.isBlank() ? null : fr.trim());
    }

}
