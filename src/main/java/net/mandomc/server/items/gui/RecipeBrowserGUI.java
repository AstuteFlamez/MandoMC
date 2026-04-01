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
import net.mandomc.server.items.config.RecipeCategoryConfig.RecipeCategoryDefinition;
import net.mandomc.server.items.RecipeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recipe browser root/category GUI using core GUI system.
 */
public class RecipeBrowserGUI extends InventoryGUI {

    private static final String ROOT_TITLE = "§8Recipes";
    private static final int ROOT_SIZE = 27;

    private final GUIManager guiManager;
    private final String categoryId;

    private RecipeBrowserGUI(GUIManager guiManager, String categoryId) {
        this.guiManager = guiManager;
        this.categoryId = categoryId;
    }

    public static RecipeBrowserGUI root(GUIManager guiManager) {
        return new RecipeBrowserGUI(guiManager, null);
    }

    public static RecipeBrowserGUI category(GUIManager guiManager, String categoryId) {
        return new RecipeBrowserGUI(guiManager, categoryId);
    }

    @Override
    protected Inventory createInventory() {
        String title = ROOT_TITLE;
        int size = ROOT_SIZE;
        RecipeCategoryDefinition category = getCategory();
        if (category != null) {
            title = "§8Recipes » " + category.name();
            size = normalizeSize(category.guiSize());
        }
        return Bukkit.createInventory(null, size, title);
    }

    @Override
    public void decorate(Player player) {
        fillBackground();

        RecipeCategoryDefinition category = getCategory();
        if (category == null) {
            decorateRoot();
        } else {
            decorateCategory(category);
        }

        super.decorate(player);
    }

    private void decorateRoot() {
        List<RecipeCategoryDefinition> categories = RecipeCategoryConfig.getSortedCategories();
        for (RecipeCategoryDefinition category : categories) {
            int slot = category.slot();
            if (slot < 0 || slot >= getInventory().getSize()) {
                continue;
            }

            addButton(slot, new InventoryButton()
                    .creator(p -> categoryIcon(category))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        guiManager.openGUI(category(guiManager, category.id()), clicker);
                    }));
        }

    }

    private void decorateCategory(RecipeCategoryDefinition category) {
        int backSlot = getInventory().getSize() - 1;
        addButton(backSlot, new InventoryButton()
                .creator(p -> createItem(Material.ARROW, "§cBack"))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    guiManager.openGUI(root(guiManager), clicker);
                }));

        List<Map.Entry<String, Integer>> items = new ArrayList<>(category.itemSlots().entrySet());
        items.sort(Map.Entry.comparingByValue());

        for (Map.Entry<String, Integer> entry : items) {
            String itemId = entry.getKey();
            int slot = entry.getValue();

            if (!RecipeRegistry.hasRecipe(itemId)) {
                continue;
            }
            if (slot < 0 || slot >= getInventory().getSize()) {
                continue;
            }

            ItemStack item = ItemRegistry.get(itemId);

            addButton(slot, new InventoryButton()
                    .creator(p -> item != null ? item : createItem(Material.BARRIER, "§c" + itemId))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        guiManager.openGUI(
                                RecipeViewerGUI.of(guiManager, itemId, () -> category(guiManager, category.id())),
                                clicker
                        );
                    }));
        }
    }

    private RecipeCategoryDefinition getCategory() {
        if (categoryId == null) {
            return null;
        }
        return RecipeCategoryConfig.getCategory(categoryId);
    }

    private void fillBackground() {
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> pane)
                    .consumer(event -> {}));
        }
    }

    private static int normalizeSize(int value) {
        if (value < 9) return 9;
        if (value > 54) return 54;
        return (value / 9) * 9;
    }

    private static ItemStack categoryIcon(RecipeCategoryDefinition category) {
        ItemStack base = ItemRegistry.get(category.icon());

        if (base == null) {
            Material material = Material.matchMaterial(category.icon());
            if (material != null) {
                base = new ItemStack(material);
            } else {
                base = new ItemStack(Material.BARRIER);
            }
        }

        ItemStack display = base.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(category.name());
            display.setItemMeta(meta);
        }
        return display;
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