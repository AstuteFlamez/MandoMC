package net.mandomc.server.items.gui;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.RecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RecipeViewerGUI extends InventoryGUI {

    private static final String TITLE = "§8Recipe Viewer";
    private static final int[] CRAFT_GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};

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
        return Bukkit.createInventory(null, 54, TITLE);
    }

    @Override
    public void decorate(Player player) {
        fillBackground();
        addBackButton();

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

    private void addBackButton() {
        addButton(53, new InventoryButton()
                .creator(player -> createItem(Material.ARROW, "§cBack"))
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

    private void fillBackground() {
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> pane)
                    .consumer(event -> {}));
        }
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
        return createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
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
