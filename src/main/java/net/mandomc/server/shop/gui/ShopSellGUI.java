package net.mandomc.server.shop.gui;

import net.mandomc.core.guis.InventoryHandler;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.server.shop.ShopSellService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;

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
        this.inventory = Bukkit.createInventory(null, SIZE, LangManager.get("shops.sell-gui-title"));
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

        ShopSellService.SellResult result = ShopSellService.processInventory(inventory.getContents());

        // Return unsellable items; drop at feet if inventory is full
        for (ItemStack item : result.unsellable()) {
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(dropped ->
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
        }

        if (result.totalSold() == 0) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        EconomyModule.deposit(player, result.totalProfit());

        player.sendMessage(LangManager.get("shops.sold", "%amount%", String.valueOf(result.totalSold()), "%profit%", EconomyModule.format(result.totalProfit())));
    }
}
