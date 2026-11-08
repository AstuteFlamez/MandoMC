package net.mandomc.gameplay.arrival;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArrivalSettingsTest {
    @Test
    public void acceptsDistinctNamespacedWorlds() {
        var settings = new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld", 5);
        assertEquals(5, settings.warmupSeconds());
    }

    @Test
    public void rejectsMissingAndAmbiguousWorldNames() {
        for (var invalid : new String[] {null, "", "SpawnWorld", "minecraft:SpawnWorld", "../../world"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArrivalSettings(invalid, "minecraft:overworld", 5));
        }
    }

    @Test
    public void rejectsSurvivalPointingToProtectedOutpost() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalSettings("minecraft:spawnworld", "minecraft:spawnworld", 5));
    }

    @Test
    public void rejectsInvalidWarmups() {
        for (int seconds : new int[] {-1, 0, 31, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld", seconds));
        }
    }
}
