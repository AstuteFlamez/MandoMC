package net.mandomc.gameplay.abilities.model;

import java.util.Objects;

/**
 * Hotbar binding for a single ability.
 */
public final class AbilityBinding {
    private final String abilityId;
    private final int selectedLevel;

    public AbilityBinding(String abilityId) {
        this(abilityId, 0);
    }

    public AbilityBinding(String abilityId, int selectedLevel) {
        this.abilityId = Objects.requireNonNull(abilityId, "abilityId");
        this.selectedLevel = Math.max(0, selectedLevel);
    }

    public String abilityId() {
        return abilityId;
    }

    public int selectedLevel() {
        return selectedLevel;
    }
}
