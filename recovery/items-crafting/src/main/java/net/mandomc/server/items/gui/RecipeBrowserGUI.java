package net.mandomc.server.items.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.config.RecipeCategoryConfig;
import net.mandomc.server.items.RecipeRegistry;
import net.mandomc.server.shop.gui.ShopGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe browser GUI using ordered entries from recipes.yml.
 */
public class RecipeBrowserGUI extends InventoryGUI {
    private static final int SIZE = 54;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_NEXT = 50;
    private static final int MODEL_BLANK = 5;
    private static final int MODEL_PREVIOUS = 2;
    private static final int MODEL_NEXT = 1;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final GUIManager guiManager;
    private final int page;

    private RecipeBrowserGUI(GUIManager guiManager, int page) {
        this.guiManager = guiManager;
        this.page = Math.max(0, page);
    }

    public static RecipeBrowserGUI root(GUIManager guiManager) {
        return new RecipeBrowserGUI(guiManager, 0);
    }

    public static RecipeBrowserGUI page(GUIManager guiManager, int page) {
        return new RecipeBrowserGUI(guiManager, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, title());
    }

    @Override
    public void decorate(Player player) {
        List<String> orderedIds = new ArrayList<>();
        for (String id : RecipeCategoryConfig.getOrderedRecipeIds()) {
            if (RecipeRegistry.hasRecipe(id)) {
                orderedIds.add(id);
            }
        }

        int pageSize = CONTENT_SLOTS.length;
        int maxPage = orderedIds.isEmpty() ? 0 : (orderedIds.size() - 1) / pageSize;
        int currentPage = Math.min(page, maxPage);
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, orderedIds.size());

        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            int slot = CONTENT_SLOTS[slotIndex++];
            String itemId = orderedIds.get(i);
            ItemStack item = ItemRegistry.get(itemId);

            addButton(slot, new InventoryButton()
                    .creator(p -> item != null ? item : createItem(Material.BARRIER, "§c" + itemId))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        guiManager.openGUI(
                                RecipeViewerGUI.of(guiManager, itemId, () -> page(guiManager, currentPage)),
                                clicker
                        );
                    }));
        }

        addButton(SLOT_INFO, new InventoryButton()
                .creator(p -> createInfoItem())
                .consumer(event -> {}));

        if (currentPage > 0) {
            addButton(SLOT_PREVIOUS, new InventoryButton()
                    .creator(p -> createNavItem("§9Previous Page", MODEL_PREVIOUS))
                    .consumer(event -> guiManager.openGUI(page(guiManager, currentPage - 1), player)));
        }

        if (end < orderedIds.size()) {
            addButton(SLOT_NEXT, new InventoryButton()
                    .creator(p -> createNavItem("§9Next Page", MODEL_NEXT))
                    .consumer(event -> guiManager.openGUI(page(guiManager, currentPage + 1), player)));
        }

        super.decorate(player);
    }

    private static String title() {
        String base = removeGlyphOccurrences(ShopGUI.SHOP_TITLE.substring(0, ShopGUI.SHOP_TITLE.length() - 1), '', 11);
        return base + "§fį";
    }

    private static ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(MODEL_BLANK);
            meta.setDisplayName("§b§lRecipe Guide");
            meta.setLore(List.of(
                    "§7Not sure how to find an item?",
                    "§fRead the item's lore §7for clues."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNavItem(String name, int modelData) {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String removeGlyphOccurrences(String text, char glyph, int removeCount) {
        StringBuilder sb = new StringBuilder(text.length());
        int removed = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == glyph && removed < removeCount) {
                removed++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static ItemStack createItem(Material material, String name) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }

        return item;
    }
}