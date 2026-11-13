package net.mandomc.gameplay.mobs;

import java.io.InputStreamReader;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterPlanTest {
    @org.junit.Test public void creativePlayersAreEligibleButSpectatorsRemainExcluded() {
        for (org.bukkit.GameMode mode : org.bukkit.GameMode.values()) {
            org.junit.Assert.assertEquals(mode != org.bukkit.GameMode.SPECTATOR, SandySpawns.eligible(mode));
        }
    }
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/mobs/spawning.yml"))));
    }
    @Test public void patrolBudgetsUseBaseStatsAcrossManyRolls() {
        var settings = SandySettings.read(config());
        var random = new Random(20391);
        var leaders = new HashSet<Integer>();
        boolean full = false, noCompanions = false;
        for (int i = 0; i < 10000; i++) {
            var plan = EncounterPlan.roll(EncounterPlan.Kind.PATROL, random, settings);
            leaders.add(plan.leaderLevel());
            assertTrue(plan.members().size() <= 6);
            long raiders = plan.members().stream().filter(m -> m.type().startsWith("Tusken")).count();
            assertTrue(raiders >= 2 && raiders <= 4);
            assertTrue(plan.members().stream().anyMatch(m -> m.type().equals("TuskenRaiderSniper")));
            assertTrue(plan.members().stream().anyMatch(m -> m.type().equals("TuskenRaiderMelee")));
            for (String companion : List.of("Bantha", "Massiff")) assertTrue(plan.members().stream().filter(m -> m.type().equals(companion)).count() <= 2);
            for (var member : plan.members()) {
                assertEquals(1, member.level());
                assertTrue(Math.abs(member.level() - plan.leaderLevel()) <= 1);
            }
            full |= plan.members().size() == 6;
            noCompanions |= plan.members().size() == raiders;
        }
        assertEquals(Set.of(1), leaders);
        assertTrue(full && noCompanions);
    }
    @Test public void wildlifeNeverMixesSpeciesAndIncludesSingles() {
        var settings = SandySettings.read(config());
        var random = new Random(31);
        for (var kind : List.of(EncounterPlan.Kind.BANTHA, EncounterPlan.Kind.DEWBACK, EncounterPlan.Kind.MASSIFF)) {
            var sizes = new HashSet<Integer>();
            for (int i = 0; i < 1000; i++) {
                var plan = EncounterPlan.roll(kind, random, settings);
                sizes.add(plan.members().size());
                assertEquals(1, plan.members().stream().map(EncounterPlan.Member::type).distinct().count());
            }
            assertEquals(Set.of(1, 2, 3), sizes);
        }
    }
    @Test public void sharedCapsReserveTheEntireGroup() {
        var settings = SandySettings.read(config());
        var plan = new EncounterPlan(Collections.nCopies(6, new EncounterPlan.Member("Bantha", 1)), 1);
        assertTrue(plan.fits(6, 90, settings));
        assertFalse(plan.fits(7, 90, settings));
        assertFalse(plan.fits(6, 91, settings));
    }
    @Test public void rejectsUnboundedOrInconsistentConfiguration() {
        for (var entry : Map.of("population.group-cap", 1000, "spawn.attempts", 1000,
                "patrol.raiders-min", 1, "population.nearby-cap", 3).entrySet()) {
            var c = config(); c.set(entry.getKey(), entry.getValue());
            assertThrows(IllegalArgumentException.class, () -> SandySettings.read(c));
        }
    }
    @Test public void rifleUsesConfiguredBaseRegardlessOfLegacyLevel() {
        var settings = SandySettings.read(config());
        assertEquals(6, settings.rifleDamage(1), 0);
        assertEquals(6, settings.rifleDamage(5), 0);
        assertEquals(6, settings.rifleDamage(100), 0);
    }
}
