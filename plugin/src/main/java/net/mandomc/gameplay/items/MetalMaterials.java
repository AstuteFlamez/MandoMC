package net.mandomc.gameplay.items;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Native-backed ingots keep ordinary material recipes while custom recipes require exact catalog metadata. */
public final class MetalMaterials implements Listener {
    private final ItemCatalog items;

    public MetalMaterials(ItemCatalog items) { this.items = items; }

    public ItemStack canonical(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        String id = original.hasItemMeta()
                ? original.getItemMeta().getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING) : null;
        String metal = switch (original.getType()) {
            case IRON_INGOT -> "agrinium";
            case REDSTONE -> "electrum";
            case COPPER_INGOT -> "electrum".equals(id) ? id : null;
            // Migrate the old catalog bases without losing stable IDs or quantities.
            case ECHO_SHARD -> "agrinium".equals(id) || "electrum".equals(id) ? id : null;
            default -> null;
        };
        if (metal == null || original.hasItemMeta() && !metal.equals(id)) return original;
        var result = items.create(metal);
        result.setAmount(original.getAmount());
        return result;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void smelt(FurnaceSmeltEvent event) { event.setResult(canonical(event.getResult())); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void craft(PrepareItemCraftEvent event) {
        event.getInventory().setResult(canonical(event.getInventory().getResult()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automatic(org.bukkit.event.block.CrafterCraftEvent event) {
        event.setResult(canonical(event.getResult()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        normalize(event.getInventory());
        normalize(event.getPlayer().getInventory());
    }

    @EventHandler public void join(PlayerJoinEvent event) { normalize(event.getPlayer().getInventory()); }

    private void normalize(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var original = inventory.getItem(slot);
            var normalized = canonical(original);
            if (normalized != original && !normalized.equals(original)) inventory.setItem(slot, normalized);
        }
    }
}
