package net.mandomc.gameplay.trophies;

import java.util.ArrayList;
import java.util.List;
import net.mandomc.gameplay.trophies.TrophyJournal.Cell;
import net.mandomc.gameplay.trophies.TrophyJournal.Face;
import org.bukkit.Location;
import org.bukkit.World;

/** Grid pose shared by the footprint check, the journal and the item display. */
record PlacementPlan(Cell anchor, int yaw, Face face, int width, int height) {
    List<Cell> cells() {
        var cells = new ArrayList<Cell>(width * height);
        for (int y = 0; y < height; y++) {
            for (int offset = -(width / 2); offset <= width / 2; offset++) {
                int x = anchor.x() + (yaw % 180 == 0 ? offset : 0);
                int z = anchor.z() + (yaw % 180 == 0 ? 0 : offset);
                cells.add(new Cell(anchor.world(), x, anchor.y() + y, z));
            }
        }
        return List.copyOf(cells);
    }

    Location center(World world) {
        return new Location(world, anchor.x() + .5, anchor.y() + .5, anchor.z() + .5, yaw, 0);
    }
}
