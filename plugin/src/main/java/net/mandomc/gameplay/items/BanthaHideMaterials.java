package net.mandomc.gameplay.items;

import java.util.ArrayList;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** One native leather identity, with conservative migration of owned hides and unmodified vanilla leather. */
public final class BanthaHideMaterials implements Listener {
    private final ItemCatalog items;
    private static final Component OLD_USE = MiniMessage.miniMessage().deserialize("<gray>Used to craft Desert Robes.")
            .decoration(TextDecoration.ITALIC, false);
    public BanthaHideMaterials(JavaPlugin plugin, ItemCatalog items) {
        this.items = items;
        if (Objects.requireNonNull(items.create("bantha_hide")).getType() != Material.LEATHER)
            throw new IllegalArgumentException("bantha_hide must retain native LEATHER behavior");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(p -> normalize(p.getInventory()));
    }
    public static boolean refresh(ItemCatalog items, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getType() == Material.LEATHER && !stack.hasItemMeta()) {
            stack.setItemMeta(items.create("bantha_hide").getItemMeta()); return true;
        }
        if (stack.getType() != Material.LEATHER && stack.getType() != Material.ECHO_SHARD) return false;
        var meta = stack.getItemMeta();
        if (!"bantha_hide".equals(meta.getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING))) return false;
        boolean changed = stack.getType() != Material.LEATHER;
        if (changed) { stack.setType(Material.LEATHER); meta = stack.getItemMeta(); }
        var lore = meta.lore();
        if (lore != null && lore.contains(OLD_USE)) {
            var updated = new ArrayList<>(lore); var replacement = items.create("bantha_hide").getItemMeta().lore().getLast();
            updated.replaceAll(line -> line.equals(OLD_USE) ? replacement : line); meta.lore(updated); changed = true;
        }
        if (changed) stack.setItemMeta(meta);
        return changed;
    }
    public ItemStack canonical(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        var copy = original.clone(); return refresh(items, copy) ? copy : original;
    }
    private void normalize(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var stack = inventory.getItem(slot); if (refresh(items, stack)) inventory.setItem(slot, stack);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void death(EntityDeathEvent e) {
        e.getDrops().replaceAll(this::canonical);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void spawn(ItemSpawnEvent e) {
        e.getEntity().setItemStack(canonical(e.getEntity().getItemStack()));
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void loot(org.bukkit.event.world.LootGenerateEvent e) { e.getLoot().replaceAll(this::canonical); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void pickup(EntityPickupItemEvent e) { e.getItem().setItemStack(canonical(e.getItem().getItemStack())); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void move(InventoryMoveItemEvent e) { e.setItem(canonical(e.getItem())); }
    @EventHandler public void join(PlayerJoinEvent e) { normalize(e.getPlayer().getInventory()); }
    @EventHandler public void open(InventoryOpenEvent e) { normalize(e.getInventory()); normalize(e.getPlayer().getInventory()); }
    @EventHandler(priority=EventPriority.LOWEST) public void craft(PrepareItemCraftEvent e) {
        var matrix=e.getInventory().getMatrix(); boolean changed=false;
        for(var stack:matrix) changed |= refresh(items,stack);
        if(changed)e.getInventory().setMatrix(matrix);
        var result=canonical(e.getInventory().getResult());e.getInventory().setResult(result);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true) public void automatic(org.bukkit.event.block.CrafterCraftEvent e) { e.setResult(canonical(e.getResult())); }
}
