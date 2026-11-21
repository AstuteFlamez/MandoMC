package net.mandomc.gameplay.trophies;

import java.util.Map;
import net.mandomc.gameplay.trophies.TrophyJournal.Cell;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/** Each opt-in block supplies its existing mounting rule, grid dimensions and placed model. */
interface PreviewBlock {
    Map<String, PreviewBlock> TYPES = Map.of(RhydoniumBarrel.ITEM, new RhydoniumBarrel());

    static PreviewBlock forId(String id) {
        return id == null ? null : TYPES.get(id);
    }

    Block site(Block clicked, BlockFace face);
    PlacementPlan plan(Cell site, BlockFace face, float playerYaw);
    NamespacedKey model(ItemStack held);
    int width();
    int height();
    String messages();
}
