package net.mandomc.system.shops;

import net.mandomc.core.modules.core.EconomyModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShopPurchaseHandler {

    public static void purchase(Player player, ShopItem item, int amount, Shop shop) {

        int unitPrice = item.getPrice();

        int maxAffordable = (int) (EconomyModule.getBalance(player) / unitPrice);
        int desired = Math.min(amount, maxAffordable);

        if (desired <= 0) {
            player.sendMessage(color(shop.getMessages().prefix + shop.getMessages().notEnoughMoney));
            return;
        }

        ItemStack stack = ShopUtils.createItem(item, desired);

        var leftover = player.getInventory().addItem(stack);

        int leftoverAmount = leftover.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();

        int given = desired - leftoverAmount;

        if (given <= 0) {
            player.sendMessage(color(shop.getMessages().prefix + shop.getMessages().inventoryFull));
            return;
        }

        int cost = given * unitPrice;
        EconomyModule.withdraw(player, cost);

        player.sendMessage(color(
                shop.getMessages().prefix +
                shop.getMessages().purchased
                        .replace("%amount%", String.valueOf(given))
                        .replace("%item%", item.getId())
        ));
    }

    private static String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}