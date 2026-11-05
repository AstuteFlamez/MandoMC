package net.mandomc.gameplay.abilities.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player ability progression and binding state.
 */
public class AbilityPlayerProfile {
    private final UUID playerId;
    private AbilityClass selectedClass;
    private int skillTokens;
    private final Map<String, Integer> unlockedLevels;
    private final Map<String, Integer> selectedLevels;
    private final Map<Integer, AbilityBinding> bindings;

    public AbilityPlayerProfile(UUID playerId) {
        this(
                playerId,
                AbilityClass.UNSET,
                0,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        );
    }

    public AbilityPlayerProfile(
            UUID playerId,
            AbilityClass selectedClass,
            int skillTokens,
            Map<String, Integer> unlockedLevels,
            Map<String, Integer> selectedLevels,
            Map<Integer, AbilityBinding> bindings
    ) {
        this.playerId = playerId;
        this.selectedClass = selectedClass;
        this.skillTokens = skillTokens;
        this.unlockedLevels = unlockedLevels;
        this.selectedLevels = selectedLevels;
        this.bindings = bindings;
    }

    public UUID playerId() {
        return playerId;
    }

    public AbilityClass selectedClass() {
        return selectedClass;
    }

    public void setSelectedClass(AbilityClass selectedClass) {
        this.selectedClass = selectedClass;
    }

    public int skillTokens() {
        return skillTokens;
    }

    public void setSkillTokens(int skillTokens) {
        this.skillTokens = Math.max(0, skillTokens);
    }

    public void addSkillTokens(int amount) {
        this.skillTokens = Math.max(0, this.skillTokens + amount);
    }

    public Map<String, Integer> unlockedLevels() {
        return unlockedLevels;
    }

    public Map<String, Integer> selectedLevels() {
        return selectedLevels;
    }

    public Map<Integer, AbilityBinding> bindings() {
        return bindings;
    }

    public int unlockedLevel(String abilityId) {
        return unlockedLevels.getOrDefault(abilityId, 0);
    }

    public boolean isUnlocked(String abilityId) {
        return unlockedLevel(abilityId) > 0;
    }

    public int selectedLevel(String abilityId) {
        int unlocked = unlockedLevel(abilityId);
        if (unlocked <= 0) {
            return 0;
        }
        int selected = selectedLevels.getOrDefault(abilityId, unlocked);
        return Math.max(1, Math.min(selected, unlocked));
    }

    public void setUnlockedLevel(String abilityId, int level) {
        if (level <= 0) {
            unlockedLevels.remove(abilityId);
            selectedLevels.remove(abilityId);
            return;
        }
        unlockedLevels.put(abilityId, level);
        selectedLevels.put(abilityId, Math.max(1, Math.min(selectedLevel(abilityId), level)));
    }

    public void setSelectedLevel(String abilityId, int level) {
        int unlocked = unlockedLevel(abilityId);
        if (unlocked <= 0) {
            selectedLevels.remove(abilityId);
            return;
        }
        selectedLevels.put(abilityId, Math.max(1, Math.min(level, unlocked)));
    }
}
