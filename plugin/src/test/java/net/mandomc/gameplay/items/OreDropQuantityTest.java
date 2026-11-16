package net.mandomc.gameplay.items;

import java.util.random.RandomGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

public class OreDropQuantityTest {
    @Test public void ordinaryAndEventOreNeverRollOrMultiplyTheirDrop() {
        RandomGenerator forbidden = () -> { throw new AssertionError("Ore yield must not be random"); };
        for (int i = 0; i < 100; i++) assertEquals(1, OreDropQuantity.roll(forbidden));
    }
    @Test public void oldSheetsResolveToMatchingIngotsWithoutChangingOtherItems() {
        assertEquals("mandalorian_iron", MetalMigration.ingotId("mandalorian_sheet"));
        assertEquals("beskar", MetalMigration.ingotId("beskar_sheet"));
        assertEquals("metal_scrap", MetalMigration.ingotId("metal_scrap"));
        assertNull(MetalMigration.ingotId(null));
        for (String ingot : MetalMigration.SHEETS.values()) assertEquals(ingot, MetalMigration.ingotId(ingot));
    }
}
