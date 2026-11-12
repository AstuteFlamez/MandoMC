package net.mandomc.gameplay.mobs;

import java.io.InputStreamReader;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterOpportunityTest {
    private YamlConfiguration config(double chance) {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/mobs/spawning.yml"), java.nio.charset.StandardCharsets.UTF_8));
        config.set("spawn.chance", chance);
        return config;
    }

    @Test public void comparableLegacyAndNewRollsTripleOnlyPatrolOpportunities() {
        for (double chance : new double[] {.25, 1}) {
            var settings = SandySettings.read(config(chance));
            assertEquals(chance / 4 * 3, settings.patrolChance(), 1e-9);
            assertEquals(chance * .75, settings.wildlifeChance(), 1e-9);
            int oldPatrol = 0, oldWildlife = 0, newPatrol = 0, newWildlife = 0, lockedWildlife = 0;
            var oldRandom = new Random(341);
            var newRandom = new Random(341);
            var lockedRandom = new Random(341);
            for (int i = 0; i < 100_000; i++) {
                if (oldRandom.nextDouble() < chance) {
                    if (oldRandom.nextInt(4) == 0) oldPatrol++; else oldWildlife++;
                }
                for (var kind : EncounterPlan.opportunities(newRandom, settings, true)) {
                    if (kind == EncounterPlan.Kind.PATROL) newPatrol++; else newWildlife++;
                }
                for (var kind : EncounterPlan.opportunities(lockedRandom, settings, false)) {
                    assertNotEquals(EncounterPlan.Kind.PATROL, kind); lockedWildlife++;
                }
            }
            System.out.println("Opportunity sample chance=" + chance + " legacy patrol/wildlife=" + oldPatrol + "/" + oldWildlife
                    + " new=" + newPatrol + "/" + newWildlife + " locked wildlife=" + lockedWildlife);
            assertEquals(3, (double) newPatrol / oldPatrol, .1);
            assertEquals(oldWildlife, newWildlife, 1000);
            assertEquals(oldWildlife, lockedWildlife, 1000);
        }
    }

    @Test public void multiplierAndDisengagementAreValidatedAndDoNotAlterEncounterBudgets() {
        var config = config(1);
        config.set("spawn.patrol-frequency-multiplier", 16);
        var settings = SandySettings.read(config);
        assertEquals(1, settings.patrolChance(), 0);
        assertEquals(6, settings.groupCap()); assertEquals(12, settings.localCap()); assertEquals(96, settings.worldCap());
        for (double bad : new double[] {-1, 17, Double.NaN}) {
            config.set("spawn.patrol-frequency-multiplier", bad);
            assertThrows(IllegalArgumentException.class, () -> SandySettings.read(config));
        }
        config.set("spawn.patrol-frequency-multiplier", 3);
        config.set("patrol.disengage-seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> SandySettings.read(config));
    }
}
