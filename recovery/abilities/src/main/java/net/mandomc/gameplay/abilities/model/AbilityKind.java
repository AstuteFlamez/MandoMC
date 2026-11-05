package net.mandomc.gameplay.abilities.model;

import java.util.Locale;

/**
 * Runtime handler kind used to execute an ability.
 */
public enum AbilityKind {
    FORCE_JUMP,
    FORCE_PUSH,
    NONE;

    public static AbilityKind fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return AbilityKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
