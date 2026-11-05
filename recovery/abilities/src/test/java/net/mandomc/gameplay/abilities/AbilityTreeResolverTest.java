package net.mandomc.gameplay.abilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.config.AbilityLevelDefinition;
import net.mandomc.gameplay.abilities.config.ParticlePreset;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityKind;
import net.mandomc.gameplay.abilities.model.AbilityNodeState;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityTreeResolver;

class AbilityTreeResolverTest {

    @Test
    void rootNodeIsUnlockableAndChildDependsOnParent() {
        AbilityLevelDefinition level = new AbilityLevelDefinition(
                1, 1, 2.0, 0.0, 1.0, 6.0, 0.0,
                new ParticlePreset(Particle.CLOUD, 1, 0, 0, 0, 0)
        );
        AbilityDefinition root = new AbilityDefinition(
                "root",
                "Root",
                AbilityClass.JEDI,
                AbilityKind.FORCE_PUSH,
                10,
                -1,
                true,
                List.of(),
                Map.of(1, level)
        );
        AbilityDefinition child = new AbilityDefinition(
                "child",
                "Child",
                AbilityClass.JEDI,
                AbilityKind.FORCE_PUSH,
                11,
                -1,
                true,
                List.of("root"),
                Map.of(1, level)
        );

        AbilityPlayerProfile profile = new AbilityPlayerProfile(UUID.randomUUID());
        profile.setSelectedClass(AbilityClass.JEDI);
        AbilityTreeResolver resolver = new AbilityTreeResolver();

        Map<String, AbilityDefinition> defs = Map.of("root", root, "child", child);
        assertEquals(AbilityNodeState.UNLOCKABLE, resolver.stateFor(profile, root, defs));
        assertEquals(AbilityNodeState.LOCKED, resolver.stateFor(profile, child, defs));

        profile.setUnlockedLevel("root", 1);
        assertEquals(AbilityNodeState.UNLOCKED, resolver.stateFor(profile, root, defs));
        assertEquals(AbilityNodeState.UNLOCKABLE, resolver.stateFor(profile, child, defs));
    }
}
