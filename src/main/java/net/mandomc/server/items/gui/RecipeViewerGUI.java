package net.mandomc.server.items.gui;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.LangManager;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.RecipeRegistry;
import net.mandomc.server.shop.gui.ShopGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RecipeViewerGUI extends InventoryGUI {

    private static final String CRAFTING_TITLE = shiftedTitle("İ");
    private static final String SMELTING_TITLE = shiftedTitle("ı");
    private static final int[] CRAFT_GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int SLOT_RETURN_MAIN = 48;
    private static final int SLOT_BACK = 50;
    private static final int BLANK_MODEL_DATA = 5;

    @FunctionalInterface
    public interface BackFactory {
        InventoryGUI create();
    }

    private final GUIManager guiManager;
    private final String itemId;
    private final BackFactory backFactory;

    private RecipeViewerGUI(GUIManager guiManager, String itemId, BackFactory backFactory) {
        this.guiManager = guiManager;
        this.itemId = itemId;
        this.backFactory = backFactory;
    }

    public static RecipeViewerGUI of(GUIManager guiManager, String itemId, BackFactory backFactory) {
        return new RecipeViewerGUI(guiManager, itemId, backFactory);
    }

    @Override
    protected Inventory createInventory() {
        String title = RecipeRegistry.getCrafting(itemId) != null ? CRAFTING_TITLE : SMELTING_TITLE;
        return Bukkit.createInventory(null, 54, LangManager.colorize(ensureWhiteBeforeSuffix(title)));
    }

    @Override
    public void decorate(Player player) {
        addFooterButtons();

        RecipeRegistry.CraftingRecipeData crafting = RecipeRegistry.getCrafting(itemId);
        if (crafting != null) {
            decorateCrafting(crafting);
        } else {
            RecipeRegistry.SmeltingRecipeData smelting = RecipeRegistry.getSmelting(itemId);
            if (smelting != null) {
                decorateSmelting(smelting);
            }
        }

        super.decorate(player);
    }

    private void decorateCrafting(RecipeRegistry.CraftingRecipeData data) {
        int index = 0;
        for (String row : data.shape) {
            for (char symbol : row.toCharArray()) {
                int slot = CRAFT_GRID_SLOTS[index++];
                if (symbol == ' ') {
                    addStaticSlot(slot, emptySlot());
                } else {
                    addIngredientSlot(slot, data.ingredients.get(symbol));
                }
            }
        }

        addStaticSlot(23, createItem(Material.CRAFTING_TABLE, "§7Crafting"));
        ItemStack result = ItemRegistry.get(itemId);
        addStaticSlot(25, result != null ? result : createItem(Material.BARRIER, "§c" + itemId));
    }

    private void decorateSmelting(RecipeRegistry.SmeltingRecipeData data) {
        addIngredientSlot(20, data.input);
        addStaticSlot(22, getFurnace(data.furnace));
        ItemStack result = ItemRegistry.get(itemId);
        addStaticSlot(24, result != null ? result : createItem(Material.BARRIER, "§c" + itemId));
    }

    private void addFooterButtons() {
        addButton(SLOT_RETURN_MAIN, new InventoryButton()
                .creator(player -> createBlankActionItem("§9Return to Main Menu"))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    guiManager.openGUI(RecipeBrowserGUI.root(guiManager), clicker);
                }));

        addButton(SLOT_BACK, new InventoryButton()
                .creator(player -> createBlankActionItem("§cBack"))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    guiManager.openGUI(backFactory.create(), clicker);
                }));
    }

    private void addIngredientSlot(int slot, String sourceId) {
        ItemStack icon = resolveItem(sourceId);
        if (icon == null) {
            icon = emptySlot();
        }

        ItemStack finalIcon = icon;
        addButton(slot, new InventoryButton()
                .creator(player -> finalIcon)
                .consumer(event -> {
                    if (sourceId == null || !RecipeRegistry.hasRecipe(sourceId)) {
                        return;
                    }
                    Player clicker = (Player) event.getWhoClicked();
                    String current = itemId;
                    BackFactory returnHere = () -> of(guiManager, current, backFactory);
                    guiManager.openGUI(of(guiManager, sourceId, returnHere), clicker);
                }));
    }

    private void addStaticSlot(int slot, ItemStack item) {
        addButton(slot, new InventoryButton()
                .creator(player -> item)
                .consumer(event -> {}));
    }

    private static ItemStack resolveItem(String id) {
        if (id == null) return null;
        ItemStack custom = ItemRegistry.get(id);
        if (custom != null) return custom;
        Material material = Material.matchMaterial(id);
        return material != null ? new ItemStack(material) : null;
    }

    private static ItemStack getFurnace(String type) {
        Material material = switch (type.toLowerCase()) {
            case "blast" -> Material.BLAST_FURNACE;
            case "smoker" -> Material.SMOKER;
            default -> Material.FURNACE;
        };
        return createItem(material, "§7Smelting");
    }

    private static ItemStack emptySlot() {
        return null;
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

    private static ItemStack createBlankActionItem(String name) {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(BLANK_MODEL_DATA);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String shiftedTitle(String suffix) {
        String base = ShopGUI.SHOP_TITLE.substring(0, ShopGUI.SHOP_TITLE.length() - 1);
        return removeGlyphOccurrences(base, '', 0) + suffix;
    }

    private static String ensureWhiteBeforeSuffix(String title) {
        if (title == null || title.length() < 1) {
            return "&f";
        }
        String body = title.substring(0, title.length() - 1);
        String suffix = title.substring(title.length() - 1);
        return body + "&f" + suffix;
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
}
