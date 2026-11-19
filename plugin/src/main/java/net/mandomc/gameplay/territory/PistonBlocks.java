package net.mandomc.gameplay.territory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/** A piston can affect its base, its head, and the sources and destinations of moved blocks. */
public final class PistonBlocks {
    private PistonBlocks() { }

    public static Set<Block> affected(BlockPistonEvent event) {
        Block base = event.getBlock();
        var direction = event.getDirection();
        // Paper reports inward movement for a sticky pull, but outward facing for an empty retract.
        var facing = base.getBlockData() instanceof Directional data ? data.getFacing() : direction;
        var moved = switch (event) {
            case BlockPistonExtendEvent extend -> extend.getBlocks();
            case BlockPistonRetractEvent retract -> retract.getBlocks();
            default -> List.<Block>of();
        };

        Set<Block> blocks = new LinkedHashSet<>();
        blocks.add(base);
        blocks.add(base.getRelative(facing));
        for (var block : moved) {
            blocks.add(block);
            blocks.add(block.getRelative(direction));
        }
        return blocks;
    }
}
