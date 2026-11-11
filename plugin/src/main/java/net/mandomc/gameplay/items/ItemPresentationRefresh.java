package net.mandomc.gameplay.items;

import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Refresh known presentation fields on older catalog stacks without replacing their other metadata. */
public final class ItemPresentationRefresh implements Listener {
    private final ItemCatalog items;

    public ItemPresentationRefresh(JavaPlugin plugin, ItemCatalog items) {
        this.items = items;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(player -> refresh(player.getInventory()));
    }

    public static boolean refresh(ItemCatalog items, ItemStack item) {
        String id = items.identify(item);
        if (LegacyFacilityLore.affected(id)) {
            var meta = item.getItemMeta();
            var before = meta.lore();
            var after = LegacyFacilityLore.clean(before);
            if (after == before) return false;
            meta.lore(after);
            item.setItemMeta(meta);
            return true;
        }
        if (id != null && Set.of("speederbike", "xwing", "tiefighter", "uwing").contains(id)) {
            // Change only the badge row: packed health, identity tokens and other lore stay intact.
            var meta = item.getItemMeta();
            var lore = meta.lore();
            var badge = items.create(id).getItemMeta().lore().getFirst();
            if (lore == null || lore.isEmpty() || lore.getFirst().equals(badge)) return false;
            var updated = new java.util.ArrayList<>(lore);
            updated.set(0, badge);
            meta.lore(updated);
            item.setItemMeta(meta);
            return true;
        }
        if (id == null || !Set.of("bantha_hide", "massiff_ribs", "dewback_leg", "durasteel_wrench").contains(id)) return false;
        var template = items.create(id);
        var current = template.getItemMeta();
        var food = template.getData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD);
        var meta = item.getItemMeta();
        if (java.util.Objects.equals(meta.lore(), current.lore())
                && java.util.Objects.equals(meta.getItemModel(), current.getItemModel())
                && java.util.Objects.equals(item.getData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD), food)) return false;
        meta.lore(current.lore());
        meta.setItemModel(current.getItemModel());
        item.setItemMeta(meta);
        if (food != null) item.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD, food);
        return true;
    }

    private void refresh(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (item != null && refresh(items, item)) inventory.setItem(slot, item);
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) { refresh(event.getPlayer().getInventory()); }
    @EventHandler public void open(InventoryOpenEvent event) {
        refresh(event.getInventory());
        refresh(event.getPlayer().getInventory());
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player)) return;
        var stack = event.getItem().getItemStack();
        if (LegacyFacilityLore.affected(items.identify(stack)) && refresh(items, stack))
            event.getItem().setItemStack(stack);
    }

}
