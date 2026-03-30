package net.mandomc.system.shops;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.core.guis.InventoryHandler;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.system.items.ItemKeys;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.core.LangManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A free-form 6-row sell inventory.
 *
 * Players drag in any items they want to sell. On close:
 *  - Items matching a shop entry with sell_price > 0 are sold for money.
 *  - All other items are returned to the player (dropped at feet if inventory full).
 *
 * Does NOT extend InventoryGUI — player must be able to freely place items.
 * Registered via {@link net.mandomc.core.guis.GUIManager#registerHandledInventory}.
 */
public class ShopSellGUI implements InventoryHandler {

    private static final int SIZE = 54;

    private final Inventory inventory;

    public ShopSellGUI(Player target) {
        this.inventory = Bukkit.createInventory(null, SIZE, "&6Sell Items");
    }

    public Inventory getInventory() {
        return inventory;
    }

    /** Allow all clicks — players freely place and take items. */
    @Override
    public void onClick(InventoryClickEvent event) {}

    @Override
    public void onOpen(InventoryOpenEvent event) {}

    @Override
    public void onClose(InventoryCloseEvent event) {

        if (!(event.getPlayer() instanceof Player player)) return;

        double totalProfit = 0;
        int totalSold = 0;
        List<ItemStack> unsellable = new ArrayList<>();

        for (ItemStack item : inventory.getContents()) {

            if (item == null || item.getType() == Material.AIR) continue;

            int sellPrice = findSellPrice(item);

            if (sellPrice > 0) {
                totalProfit += (double) item.getAmount() * sellPrice;
                totalSold += item.getAmount();
            } else {
                unsellable.add(item.clone());
            }
        }

        // Return unsellable items; drop at feet if inventory is full
        for (ItemStack item : unsellable) {
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(dropped ->
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
        }

        if (totalSold == 0) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        EconomyModule.deposit(player, totalProfit);

        player.sendMessage(LangManager.get("shops.sold", "%amount%", String.valueOf(totalSold), "%profit%", EconomyModule.format(totalProfit)));
    }

    /**
     * Returns the sell price per item for the given stack, or -1 if unsellable.
     * Iterates all registered shops until a matching, sellable ShopItem is found.
     */
    private int findSellPrice(ItemStack item) {

        for (Shop shop : ShopManager.getAll().values()) {
            for (ShopItem shopItem : shop.getItems().values()) {

                if (shopItem.getSellPrice() <= 0) continue;

                if (matchesShopItem(item, shopItem)) {
                    return shopItem.getSellPrice();
                }
            }
        }

        return -1;
    }

    private boolean matchesShopItem(ItemStack item, ShopItem shopItem) {
        return switch (shopItem.getType()) {

            case VANILLA -> {
                Material mat = Material.matchMaterial(shopItem.getId());
                yield mat != null && item.getType() == mat;
            }

            case CUSTOM -> {
                ItemMeta meta = item.getItemMeta();
                if (meta == null) yield false;
                String id = meta.getPersistentDataContainer()
                        .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
                yield shopItem.getId().equalsIgnoreCase(id);
            }

            case WEAPON_MECHANICS_AMMO -> {
                // No public isAmmoItem() API — generate the template and compare via isSimilar()
                ItemStack template = WeaponMechanicsAPI.generateAmmo(shopItem.getId(), false);
                yield template != null && template.isSimilar(item);
            }

            case WEAPON_MECHANICS_WEAPON -> {
                String title = WeaponMechanicsAPI.getWeaponTitle(item);
                yield shopItem.getId().equals(title);
            }
        };
    }
}
