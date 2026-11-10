package net.mandomc.gameplay.items;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Replaces successful native ore loot with catalog scraps, retaining native quantities, tools, XP and cancellation. */
public final class OreDrops implements Listener {
    private record Tool(UUID world, int x, int y, int z, boolean silk) {
        boolean matches(org.bukkit.block.Block block) { return world.equals(block.getWorld().getUID())
                && x == block.getX() && y == block.getY() && z == block.getZ(); }
    }
    private final ItemCatalog items;
    private final Map<UUID, Tool> tools = new HashMap<>();
    public OreDrops(ItemCatalog items) {
        this.items = items;
        for (String id : List.of("durasteel_scrap", "electrum_scrap", "agrinium_scrap", "beskar_scrap"))
            Objects.requireNonNull(items.create(id), "Missing ore loot item: " + id);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void tool(BlockBreakEvent event) {
        // Debris normally drops itself too; remember the actual tool before native durability can break it.
        if (event.getBlock().getType() != Material.ANCIENT_DEBRIS) return;
        var block = event.getBlock();
        tools.put(event.getPlayer().getUniqueId(), new Tool(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)));
    }
    @EventHandler public void quit(PlayerQuitEvent event) { tools.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(BlockDropItemEvent event) {
        Material ore = event.getBlockState().getType(); Material expected; String id;
        if (durasteelOre(ore)) { expected = Material.DIAMOND; id = "durasteel_scrap"; }
        else if (electrumOre(ore)) { expected = Material.REDSTONE; id = "electrum_scrap"; }
        else if (ore == Material.IRON_ORE || ore == Material.DEEPSLATE_IRON_ORE) { expected = Material.RAW_IRON; id = "agrinium_scrap"; }
        else if (ore == Material.NETHER_GOLD_ORE) { expected = Material.GOLD_NUGGET; id = "beskar_scrap"; }
        else if (ore == Material.ANCIENT_DEBRIS) {
            var tool = tools.remove(event.getPlayer().getUniqueId());
            boolean silk = tool != null && tool.matches(event.getBlock()) ? tool.silk()
                    : event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH);
            if (silk) return;
            expected = Material.ANCIENT_DEBRIS; id = "beskar_scrap";
        } else return;
        for (var entity : event.getItems()) {
            var vanilla = entity.getItemStack(); if (vanilla.getType() != expected) continue;
            var scrap = Objects.requireNonNull(items.create(id)); scrap.setAmount(vanilla.getAmount()); entity.setItemStack(scrap);
        }
    }
    public static boolean durasteelOre(Material material) { return material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE; }
    public static boolean electrumOre(Material material) { return material == Material.REDSTONE_ORE || material == Material.DEEPSLATE_REDSTONE_ORE; }
}
