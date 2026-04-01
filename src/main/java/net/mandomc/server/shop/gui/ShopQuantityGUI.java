package net.mandomc.server.shop.gui;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.server.shop.ShopLoader;
import net.mandomc.server.shop.ShopPurchaseHandler;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopQuantityGUI extends InventoryGUI {

    private static final int SIZE = 54;
    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 64;

    private final Shop shop;
    private final ShopItem item;
    private final GUIManager guiManager;
    private int amount = 1;

    public ShopQuantityGUI(Shop shop, ShopItem item, GUIManager guiManager) {
        this.shop = shop;
        this.item = item;
        this.guiManager = guiManager;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, color("&8Purchase Selector"));
    }

    @Override
    public void decorate(Player player) {
        addFiller();
        addButtons(player);
        super.decorate(player);
    }

    private void addFiller() {
        InventoryButton filler = new InventoryButton()
                .creator(p -> simpleItem(Material.GRAY_STAINED_GLASS_PANE, "&8 "))
                .consumer(event -> event.setCancelled(true));

        for (int i = 0; i < SIZE; i++) {
            addButton(i, filler);
        }
    }

    private void addButtons(Player player) {
        addButton(22, new InventoryButton()
                .creator(p -> buildPreviewItem())
                .consumer(event -> event.setCancelled(true)));

        addButton(20, deltaButton("-16", -16));
        addButton(21, deltaButton("-1", -1));
        addButton(23, deltaButton("+1", 1));
        addButton(24, deltaButton("+16", 16));

        addButton(39, new InventoryButton()
                .creator(p -> confirmButton())
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player clicker)) {
                        return;
                    }
                    ShopPurchaseHandler.purchase(clicker, item, amount, shop);
                    clicker.closeInventory();
                }));

        addButton(41, new InventoryButton()
                .creator(p -> simpleItem(Material.BARRIER, "&cCancel"))
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player clicker)) {
                        return;
                    }
                    guiManager.openGUI(new ShopGUI(shop, guiManager), clicker);
                }));
    }

    private InventoryButton deltaButton(String text, int delta) {
        boolean positive = delta > 0;
        Material material = positive ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String colorPrefix = positive ? "&a" : "&c";
        return new InventoryButton()
                .creator(p -> simpleItem(material, colorPrefix + text))
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) {
                        return;
                    }
                    amount = clamp(amount + delta);
                    decorate(player);
                    player.updateInventory();
                });
    }

    private ItemStack buildPreviewItem() {
        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());
        if (base == null) {
            return simpleItem(Material.BARRIER, "&cUnavailable");
        }

        ItemStack preview = base.clone();
        preview.setAmount(amount);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(color("&7Price each: &a$" + item.getBuyPrice()));
            lore.add(color("&7Use +/- buttons to adjust amount"));
            lore.add(color("&7Total: &a$" + EconomyModule.format(amount * (double) item.getBuyPrice())));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private ItemStack confirmButton() {
        ItemStack stack = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&aConfirm Purchase"));
            meta.setLore(List.of(
                    color("&7Buy &f" + amount + "x &7item(s)"),
                    color("&7Cost: &a$" + EconomyModule.format(amount * (double) item.getBuyPrice()))
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack simpleItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static int clamp(int value) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, value));
    }

    private static String color(String text) {
        return LangManager.colorize(text);
    }
}
