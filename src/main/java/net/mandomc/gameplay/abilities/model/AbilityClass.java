package net.mandomc.gameplay.abilities.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Playable combat classes for the abilities system.
 */
public enum AbilityClass {
    UNSET,
    JEDI,
    SITH,
    MANDALORIAN;

    public static Optional<AbilityClass> fromInput(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AbilityClass.valueOf(input.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
