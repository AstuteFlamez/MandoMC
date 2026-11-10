package net.mandomc.gameplay.items;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;

/** Replaces native diamond/redstone loot after successful mining; never creates a second drop path. */
public final class OreDrops implements Listener {
    private final ItemCatalog items;

    public OreDrops(ItemCatalog items) {
        this.items = items;
        Objects.requireNonNull(items.create("durasteel_scrap"), "Missing ore loot item: durasteel_scrap");
        Objects.requireNonNull(items.create("electrum"), "Missing ore loot item: electrum");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(BlockDropItemEvent event) {
        var ore = event.getBlockState().getType();
        boolean durasteel = durasteelOre(ore);
        if (!durasteel && !electrumOre(ore)) return;
        for (var entity : event.getItems()) {
            var vanilla = entity.getItemStack();
            if (vanilla.getType() != (durasteel ? Material.DIAMOND : Material.REDSTONE)) continue;
            var scrap = Objects.requireNonNull(items.create(durasteel ? "durasteel_scrap" : "electrum"));
            scrap.setAmount(vanilla.getAmount());
            entity.setItemStack(scrap);
        }
        // Empty loot (wrong tool/Creative) and Silk Touch ore items remain entirely native, including XP.
    }

    public static boolean durasteelOre(Material material) {
        return material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE;
    }

    public static boolean electrumOre(Material material) {
        return material == Material.REDSTONE_ORE || material == Material.DEEPSLATE_REDSTONE_ORE;
    }
}
