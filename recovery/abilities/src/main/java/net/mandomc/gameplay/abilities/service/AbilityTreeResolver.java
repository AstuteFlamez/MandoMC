package net.mandomc.gameplay.abilities.service;

import java.util.List;
import java.util.Map;

import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityNodeState;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;

/**
 * Computes node state for class ability trees.
 */
public final class AbilityTreeResolver {
    public AbilityNodeState stateFor(
            AbilityPlayerProfile profile,
            AbilityDefinition definition,
            Map<String, AbilityDefinition> allDefinitions
    ) {
        if (profile == null || definition == null) {
            return AbilityNodeState.LOCKED;
        }
        if (profile.selectedClass() != definition.abilityClass()) {
            return AbilityNodeState.LOCKED;
        }
        if (profile.isUnlocked(definition.id())) {
            return AbilityNodeState.UNLOCKED;
        }
        if (definition.requires().isEmpty()) {
            return AbilityNodeState.UNLOCKABLE;
        }
        List<String> requirements = definition.requires();
        for (String requiredId : requirements) {
            AbilityDefinition required = allDefinitions.get(requiredId);
            if (required == null) {
                return AbilityNodeState.LOCKED;
            }
            if (required.abilityClass() != profile.selectedClass()) {
                return AbilityNodeState.LOCKED;
            }
            if (!profile.isUnlocked(requiredId)) {
                return AbilityNodeState.LOCKED;
            }
        }
        return AbilityNodeState.UNLOCKABLE;
    }

    public boolean canProgress(AbilityPlayerProfile profile, AbilityDefinition definition) {
        if (profile.selectedClass() != definition.abilityClass()) {
            return false;
        }
        int unlocked = profile.unlockedLevel(definition.id());
        return unlocked < definition.maxLevel();
    }

    public boolean isClassAbility(AbilityClass abilityClass, AbilityDefinition definition) {
        return definition != null && definition.abilityClass() == abilityClass;
    }
}
