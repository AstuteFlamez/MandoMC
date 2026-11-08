package net.mandomc.gameplay.arrival;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ArrivalSettingsTest {
    @Test
    public void acceptsDistinctNamespacedWorlds() {
        var settings = new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld");
        assertEquals("minecraft:overworld", settings.survivalWorld());
    }

    @Test
    public void rejectsMissingAndAmbiguousWorldNames() {
        for (var invalid : new String[] {null, "", "SpawnWorld", "minecraft:SpawnWorld", "../../world"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ArrivalSettings(invalid, "minecraft:overworld"));
        }
    }

    @Test
    public void rejectsSurvivalPointingToProtectedOutpost() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArrivalSettings("minecraft:spawnworld", "minecraft:spawnworld"));
    }

}
