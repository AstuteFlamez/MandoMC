package net.mandomc.gameplay.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;

class AbilityProfileLevelSelectionTest {

    @Test
    void selectedLevelClampsToUnlockedLevel() {
        AbilityPlayerProfile profile = new AbilityPlayerProfile(UUID.randomUUID());
        profile.setUnlockedLevel("force_push", 2);
        profile.setSelectedLevel("force_push", 99);
        assertEquals(2, profile.selectedLevel("force_push"));

        profile.setSelectedLevel("force_push", 0);
        assertEquals(1, profile.selectedLevel("force_push"));
    }
}
