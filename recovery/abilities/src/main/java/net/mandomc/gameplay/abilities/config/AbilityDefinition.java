package net.mandomc.gameplay.abilities.config;

import java.util.List;
import java.util.Map;

import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityKind;

/**
 * Static ability definition loaded from config.
 */
public record AbilityDefinition(
        String id,
        String displayName,
        AbilityClass abilityClass,
        AbilityKind kind,
        int guiSlot,
        int activationSlot,
        boolean bindable,
        List<String> requires,
        Map<Integer, AbilityLevelDefinition> levels
) {
    public int maxLevel() {
        return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }
}
