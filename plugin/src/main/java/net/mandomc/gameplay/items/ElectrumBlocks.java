package net.mandomc.gameplay.items;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

/** Records which placed storage blocks contain Electrum, independently of native copper. */
public final class ElectrumBlocks implements Listener {
    private static final String ELECTRUM_BLOCK = "electrum_block";
    private final ItemCatalog items;

    public ElectrumBlocks(ItemCatalog items) {
        this.items = items;
    }

    private static NamespacedKey key(Block block) {
        return new NamespacedKey("mandomc", "electrum_" + (block.getX() & 15) + "_"
                + block.getY() + "_" + (block.getZ() & 15));
    }

    private static boolean electrum(Block block) {
        return block.getType() == Material.WAXED_COPPER_BLOCK
                && block.getChunk().getPersistentDataContainer().has(key(block), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        var block = event.getBlockPlaced();
        var data = block.getChunk().getPersistentDataContainer();
        if (block.getType() == Material.WAXED_COPPER_BLOCK && ELECTRUM_BLOCK.equals(items.identify(event.getItemInHand()))) {
            data.set(key(block), PersistentDataType.BYTE, (byte) 1);
        } else if (block.getType() == Material.WAXED_COPPER_BLOCK) {
            data.remove(key(block));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.WAXED_COPPER_BLOCK) return;
        var block = event.getBlock();
        var data = block.getChunk().getPersistentDataContainer();
        var key = key(block);
        if (!data.has(key, PersistentDataType.BYTE)) return;
        data.remove(key);
        for (var entity : event.getItems()) {
            if (entity.getItemStack().getType() == Material.WAXED_COPPER_BLOCK) {
                var replacement = items.create(ELECTRUM_BLOCK);
                replacement.setAmount(entity.getItemStack().getAmount());
                entity.setItemStack(replacement);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(ElectrumBlocks::electrum)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(ElectrumBlocks::electrum)) event.setCancelled(true);
    }
}
