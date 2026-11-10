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
    public static final java.util.Map<Material, String> FORMS = java.util.Map.ofEntries(
            java.util.Map.entry(Material.IRON_BLOCK, "agrinium_block"),
            java.util.Map.entry(Material.IRON_ORE, "agrinium_ore"),
            java.util.Map.entry(Material.DEEPSLATE_IRON_ORE, "deepslate_agrinium_ore"),
            java.util.Map.entry(Material.DIAMOND_BLOCK, "durasteel_block"),
            java.util.Map.entry(Material.DIAMOND_ORE, "durasteel_ore"),
            java.util.Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "deepslate_durasteel_ore"),
            java.util.Map.entry(Material.REDSTONE_BLOCK, "electrum_block"),
            java.util.Map.entry(Material.REDSTONE_ORE, "electrum_ore"),
            java.util.Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "deepslate_electrum_ore"),
            java.util.Map.entry(Material.NETHERITE_BLOCK, "beskar_block"),
            java.util.Map.entry(Material.NETHER_GOLD_ORE, "beskar_ore"),
            java.util.Map.entry(Material.ANCIENT_DEBRIS, "deepslate_beskar_ore"),
            java.util.Map.entry(Material.RAW_IRON, "raw_agrinium"),
            java.util.Map.entry(Material.RAW_IRON_BLOCK, "raw_agrinium_block"));
    private final ItemCatalog items;

    public MetalMaterials(ItemCatalog items) { this.items = items; }

    public ItemStack canonical(ItemStack original) {
        if (original == null || original.isEmpty()) return original;
        String id = original.hasItemMeta()
                ? original.getItemMeta().getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING) : null;
        String form = FORMS.get(original.getType());
        if (form != null) {
            if (original.hasItemMeta()) return original;
            var result = items.create(form); result.setAmount(original.getAmount()); return result;
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockDrops(org.bukkit.event.block.BlockDropItemEvent event) {
        for (var entity : event.getItems()) entity.setItemStack(canonical(entity.getItemStack()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        event.getItem().setItemStack(canonical(event.getItem().getItemStack()));
    }

    private void normalize(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var original = inventory.getItem(slot);
            var normalized = canonical(original);
            if (normalized != original && !normalized.equals(original)) inventory.setItem(slot, normalized);
        }
    }
}
