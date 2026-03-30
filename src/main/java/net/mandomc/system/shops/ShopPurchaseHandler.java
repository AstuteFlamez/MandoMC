package net.mandomc.system.shops;

import net.mandomc.core.LangManager;
import net.mandomc.core.modules.core.EconomyModule;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Handles the purchase logic for shop items.
 *
 * Purchase flow:
 * 1. Resolve the item from its type + id.
 * 2. Calculate total cost (totalItems × buyPrice).
 * 3. Reject if the player can't afford it (no partial purchases).
 * 4. Attempt to add the item to the player's inventory.
 * 5. If the inventory is full (leftover > 0), roll back and notify.
 * 6. Withdraw funds and send a success message.
 */
public final class ShopPurchaseHandler {

    private ShopPurchaseHandler() {}

    public static void purchase(Player player, ShopItem item, int totalItems, Shop shop) {

        if (totalItems <= 0) return;

        // --- Build the actual item to give ---
        ItemStack toGive = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());

        if (toGive == null) {
            player.sendMessage(LangManager.get("shops.item-unavailable"));
            return;
        }

        toGive = toGive.clone();
        toGive.setAmount(totalItems);

        // --- Check balance ---
        double totalCost = (double) totalItems * item.getBuyPrice();

        if (!EconomyModule.has(player, totalCost)) {
            player.sendMessage(LangManager.get("shops.not-enough-money", "%cost%", EconomyModule.format(totalCost)));
            return;
        }

        // --- Try adding to inventory ---
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);

        if (!leftover.isEmpty()) {
            // Roll back: remove however much was actually placed
            int leftoverCount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            int given = totalItems - leftoverCount;

            if (given > 0) {
                // Remove items we already placed before discovering overflow
                ItemStack toRemove = toGive.clone();
                toRemove.setAmount(given);
                player.getInventory().removeItemAnySlot(toRemove);
            }

            player.sendMessage(LangManager.get("shops.inventory-full"));
            return;
        }

        // --- Withdraw and confirm ---
        EconomyModule.withdraw(player, totalCost);

        String displayName = (toGive.getItemMeta() != null && toGive.getItemMeta().hasDisplayName())
                ? toGive.getItemMeta().getDisplayName()
                : item.getId();

        player.sendMessage(LangManager.get("shops.purchased", "%amount%", String.valueOf(totalItems), "%item%", displayName, "%cost%", EconomyModule.format(totalCost)));
    }
}
