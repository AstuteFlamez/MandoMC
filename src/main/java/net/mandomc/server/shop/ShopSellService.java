package net.mandomc.server.shop;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.core.LangManager;
import net.mandomc.core.integration.OptionalPluginSupport;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.server.items.ItemKeys;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.model.ShopItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared sell logic used by both sell GUI and /sell quick actions.
 */
public final class ShopSellService {

    private ShopSellService() {}

    public static void sellHand(Player player) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        int sellPrice = findSellPrice(inHand);
        if (sellPrice <= 0) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        int soldAmount = inHand.getAmount();
        double profit = soldAmount * (double) sellPrice;

        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        EconomyModule.deposit(player, profit);
        player.sendMessage(LangManager.get(
                "shops.sold",
                "%amount%", String.valueOf(soldAmount),
                "%profit%", EconomyModule.format(profit))
        );
    }

    public static void sellAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();

        int totalSold = 0;
        double totalProfit = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            int sellPrice = findSellPrice(item);
            if (sellPrice <= 0) {
                continue;
            }

            totalSold += item.getAmount();
            totalProfit += item.getAmount() * (double) sellPrice;
            contents[i] = null;
        }

        inventory.setStorageContents(contents);

        if (totalSold == 0) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        EconomyModule.deposit(player, totalProfit);
        player.sendMessage(LangManager.get(
                "shops.sold",
                "%amount%", String.valueOf(totalSold),
                "%profit%", EconomyModule.format(totalProfit))
        );
    }

    public static SellResult processInventory(List<ItemStack> contents) {
        int totalSold = 0;
        double totalProfit = 0;
        List<ItemStack> unsellable = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            int sellPrice = findSellPrice(item);
            if (sellPrice > 0) {
                totalSold += item.getAmount();
                totalProfit += item.getAmount() * (double) sellPrice;
            } else {
                unsellable.add(item.clone());
            }
        }

        return new SellResult(totalSold, totalProfit, unsellable);
    }

    public static SellResult processInventory(ItemStack[] contents) {
        return processInventory(Arrays.asList(contents));
    }

    private static int findSellPrice(ItemStack item) {
        for (Shop shop : ShopManager.getAll().values()) {
            for (ShopItem shopItem : shop.getItems()) {
                if (shopItem.getSellPrice() <= 0) {
                    continue;
                }

                if (matchesShopItem(item, shopItem)) {
                    return shopItem.getSellPrice();
                }
            }
        }

        return -1;
    }

    private static boolean matchesShopItem(ItemStack item, ShopItem shopItem) {
        return switch (shopItem.getType()) {
            case VANILLA -> {
                Material mat = Material.matchMaterial(shopItem.getId());
                yield mat != null && item.getType() == mat;
            }
            case CUSTOM -> {
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    yield false;
                }
                String id = meta.getPersistentDataContainer()
                        .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
                yield shopItem.getId().equalsIgnoreCase(id);
            }
            case WEAPON_MECHANICS_AMMO -> {
                if (!OptionalPluginSupport.hasWeaponMechanics()) {
                    yield false;
                }
                ItemStack template = WeaponMechanicsAPI.generateAmmo(shopItem.getId(), false);
                yield template != null && template.isSimilar(item);
            }
            case WEAPON_MECHANICS_WEAPON -> {
                if (!OptionalPluginSupport.hasWeaponMechanics()) {
                    yield false;
                }
                String title = WeaponMechanicsAPI.getWeaponTitle(item);
                yield shopItem.getId().equals(title);
            }
        };
    }

    public record SellResult(int totalSold, double totalProfit, List<ItemStack> unsellable) {}
}
