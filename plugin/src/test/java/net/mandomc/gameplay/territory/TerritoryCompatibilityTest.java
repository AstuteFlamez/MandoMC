package net.mandomc.gameplay.territory;

import org.junit.Test;
import static org.junit.Assert.*;

public class TerritoryCompatibilityTest {
    @Test public void npcAndMechanismEntryPointsSurvivePresentationChanges() throws Exception {
        assertNotNull(TerritoryService.class.getMethod("spawnNpc",org.bukkit.entity.EntityType.class,
                org.bukkit.Location.class,java.util.function.Supplier.class));
        assertNotNull(TerritoryService.class.getMethod("approvedNpcSpawn",org.bukkit.event.entity.CreatureSpawnEvent.class));
        assertNotNull(TerritoryService.class.getMethod("mechanisms",org.bukkit.Location.class));
    }
}
