package net.mandomc.gameplay.trophies;

import java.util.List;
import net.mandomc.gameplay.trophies.TrophyJournal.Cell;
import net.mandomc.gameplay.trophies.TrophyJournal.Face;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlacementPlanTest {
    @Test public void percentThresholdsMatchPlacedFuelStages() {
        int[] units = {-1, 0, 124, 125, 249, 250, 374, 375, 499, 500, Integer.MAX_VALUE};
        int[] expected = {0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
        for (int i = 0; i < units.length; i++) assertEquals(expected[i], RhydoniumBarrel.stage(units[i], 500));
        assertEquals(2, RhydoniumBarrel.stage(10, 20));
        assertEquals(0, RhydoniumBarrel.stage(10, 0));
    }

    @Test public void barrelOccupiesBothLevelsAndWideFootprintsCrossChunkEdges() {
        var anchor = new Cell("minecraft:devworld", -1, 64, 15);
        assertEquals(List.of(anchor, new Cell(anchor.world(), -1, 65, 15)),
                new PlacementPlan(anchor, 0, Face.UP, 1, 2).cells());
        var alongX = new PlacementPlan(anchor, 0, Face.UP, 3, 1).cells();
        var alongZ = new PlacementPlan(anchor, 90, Face.UP, 3, 1).cells();
        assertEquals(List.of(-1, -1, 0), alongX.stream().map(c -> c.x() >> 4).toList());
        assertEquals(List.of(0, 0, 1), alongZ.stream().map(c -> c.z() >> 4).toList());
    }
}
