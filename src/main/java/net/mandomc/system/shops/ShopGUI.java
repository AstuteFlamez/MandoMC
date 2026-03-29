package net.mandomc.system.shops;

import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.guis.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ShopGUI extends InventoryGUI {

    private final GUIManager guiManager;

    // 🔥 PRE-CACHED VALUES (NO shop access in createInventory)
    private final int size;
    private final String title;

    private final Shop shop;

    public ShopGUI(GUIManager guiManager, Shop shop) {

        if (shop == null) {
            throw new IllegalArgumentException("[Shops] Shop cannot be null");
        }

        this.guiManager = guiManager;

        // 🔥 CRITICAL: store BEFORE anything else
        this.size = shop.getSize();
        this.title = shop.getTitle();

        this.shop = shop;
    }

    @Override
    protected Inventory createInventory() {
        // 🔥 SAFE — no direct shop access
        return Bukkit.createInventory(null, 54, color(title));
    }

    @Override
    public void decorate(Player player) {

        // Fill background
        if (shop.getFiller() != null) {
            for (int i = 0; i < size; i++) {
                this.addButton(i, filler(shop.getFiller().getDisplay()));
            }
        }

        // Add items
        shop.getItems().forEach((slot, item) -> {
            this.addButton(slot, createItemButton(item));
        });

        super.decorate(player);
    }

    private InventoryButton createItemButton(ShopItem item) {
        return new InventoryButton()
                .creator(player -> item.getDisplay())
                .consumer(event -> {

                    Player player = (Player) event.getWhoClicked();

                    if (event.isShiftClick()) {
                        ShopPurchaseHandler.purchase(player, item, 64, shop);
                        return;
                    }

                    // 🔥 FIX: YOU FORGOT .open(player)
                    player.showDialog(ShopDialogFactory.create(player, item, shop));
                });
    }

    private InventoryButton filler(org.bukkit.inventory.ItemStack item) {
        return new InventoryButton()
                .creator(p -> item)
                .consumer(e -> e.setCancelled(true));
    }

    private String color(String text) {
        if (text == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}