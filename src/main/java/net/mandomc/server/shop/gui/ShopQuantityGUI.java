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
    private static final int SLOT_PREVIEW = 22;
    private static final int SLOT_CONFIRM = 39;
    private static final int SLOT_CANCEL = 41;
    private static final int SLOT_BACK = SLOT_CANCEL + 8;
    private static final int MODEL_ADD = 3;
    private static final int MODEL_SUBTRACT = 4;
    private static final int MODEL_ACTION = 5;

    private final Shop shop;
    private final ShopItem item;
    private final GUIManager guiManager;
    private final int returnPage;
    private int amount = 1;

    public ShopQuantityGUI(Shop shop, ShopItem item, GUIManager guiManager, int returnPage) {
        this.shop = shop;
        this.item = item;
        this.guiManager = guiManager;
        this.returnPage = returnPage;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, color(quantityTitle(ShopGUI.SHOP_TITLE)));
    }

    @Override
    public void decorate(Player player) {
        addButtons(player);
        super.decorate(player);
    }

    private void addButtons(Player player) {
        addButton(SLOT_PREVIEW, new InventoryButton()
                .creator(p -> buildPreviewItem())
                .consumer(event -> event.setCancelled(true)));

        addButton(18, deltaButton("-32", -32, MODEL_SUBTRACT));
        addButton(19, deltaButton("-16", -16, MODEL_SUBTRACT));
        addButton(20, deltaButton("-1", -1, MODEL_SUBTRACT));
        addButton(24, deltaButton("+1", 1, MODEL_ADD));
        addButton(25, deltaButton("+16", 16, MODEL_ADD));
        addButton(26, deltaButton("+32", 32, MODEL_ADD));

        addButton(SLOT_CONFIRM, new InventoryButton()
                .creator(p -> confirmButton())
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player clicker)) {
                        return;
                    }
                    ShopPurchaseHandler.purchase(clicker, item, amount, shop);
                    clicker.closeInventory();
                }));

        addButton(SLOT_CANCEL, new InventoryButton()
                .creator(p -> actionButton("&cCancel"))
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player clicker)) {
                        return;
                    }
                    clicker.closeInventory();
                }));

        addButton(SLOT_BACK, new InventoryButton()
                .creator(p -> actionButton("&cBack Button"))
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player clicker)) {
                        return;
                    }
                    guiManager.openGUI(new ShopGUI(shop, guiManager, returnPage), clicker);
                }));
    }

    private InventoryButton deltaButton(String text, int delta, int customModelData) {
        String coloredText = delta > 0 ? "&a" + text : "&c" + text;
        return new InventoryButton()
                .creator(p -> modelButton(customModelData, coloredText, List.of(), Math.abs(delta)))
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) {
                        return;
                    }
                    amount = clamp(amount + delta, maxSelectableAmount());
                    decorate(player);
                    player.updateInventory();
                });
    }

    private ItemStack buildPreviewItem() {
        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());
        if (base == null) {
            return modelButton(MODEL_ACTION, "&cUnavailable", List.of(), 1);
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
        return modelButton(MODEL_ACTION, "&aConfirm Purchase", List.of(
                color("&7Buy &f" + amount + "x &7item(s)"),
                color("&7Cost: &a$" + EconomyModule.format(amount * (double) item.getBuyPrice()))
        ), 1);
    }

    private static ItemStack actionButton(String name) {
        return modelButton(MODEL_ACTION, name, List.of(), 1);
    }

    private static ItemStack modelButton(int customModelData, String name, List<String> lore, int amount) {
        ItemStack stack = new ItemStack(Material.FLINT);
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setCustomModelData(customModelData);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int maxSelectableAmount() {
        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());
        if (base == null) {
            return MIN_AMOUNT;
        }
        return Math.max(MIN_AMOUNT, base.getMaxStackSize());
    }

    private static int clamp(int value, int max) {
        return Math.max(MIN_AMOUNT, Math.min(max, value));
    }

    private static String color(String text) {
        return LangManager.colorize(text);
    }

    private static String quantityTitle(String shopTitle) {
        if (shopTitle == null || shopTitle.isEmpty()) {
            return "ĭ";
        }
        return shopTitle.substring(0, shopTitle.length() - 1) + "ĭ";
    }
}
