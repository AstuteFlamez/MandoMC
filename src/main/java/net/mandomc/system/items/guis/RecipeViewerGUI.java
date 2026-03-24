package net.mandomc.system.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.RecipeRegistry;

/**
 * Displays crafting and smelting recipes in GUI form.
 *
 * Supports:
 * - 3x3 crafting grid rendering
 * - Furnace / blast / smoker recipes
 * - Automatic item resolution (custom items or vanilla materials)
 */
public class RecipeViewerGUI {

    private static final String TITLE = "§8Recipe Viewer";

    /**
     * Opens the appropriate recipe viewer for an item.
     *
     * Chooses between crafting or smelting view depending on
     * what recipe is registered for the given item id.
     *
     * @param player the viewer
     * @param id     the item id
     */
    public static void open(Player player, String id) {

        if (RecipeRegistry.getCrafting(id) != null) {
            openCrafting(player, id);
            return;
        }

        if (RecipeRegistry.getSmelting(id) != null) {
            openSmelting(player, id);
        }
    }

    /*
     * =========================
     * CRAFTING
     * =========================
     */

    /**
     * Opens a crafting recipe view.
     *
     * Layout:
     * - 3x3 grid centered in GUI
     * - Crafting table icon between input and output
     * - Output item on the right
     *
     * @param player the viewer
     * @param id     the result item id
     */
    private static void openCrafting(Player player, String id) {

        Inventory gui = createGUI();

        RecipeRegistry.CraftingRecipeData data = RecipeRegistry.getCrafting(id);

        int[] grid = {10,11,12,19,20,21,28,29,30};

        int index = 0;

        for (String row : data.shape) {
            for (char c : row.toCharArray()) {

                ItemStack stack = resolveItem(data.ingredients.get(c));
                gui.setItem(grid[index], stack != null ? stack : emptySlot());

                index++;
            }
        }

        gui.setItem(23, createItem(Material.CRAFTING_TABLE, "§7Crafting"));
        gui.setItem(25, ItemRegistry.get(id));
        gui.setItem(53, createItem(Material.ARROW, "§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * SMELTING
     * =========================
     */

    /**
     * Opens a smelting recipe view.
     *
     * Layout:
     * - Input on the left
     * - Furnace type in center
     * - Output on the right
     *
     * @param player the viewer
     * @param id     the result item id
     */
    private static void openSmelting(Player player, String id) {

        Inventory gui = createGUI();

        RecipeRegistry.SmeltingRecipeData data = RecipeRegistry.getSmelting(id);

        ItemStack input = resolveItem(data.input);

        gui.setItem(20, input);
        gui.setItem(22, getFurnace(data.furnace));
        gui.setItem(24, ItemRegistry.get(id));
        gui.setItem(53, createItem(Material.ARROW, "§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * CORE HELPERS
     * =========================
     */

    /**
     * Creates the base recipe GUI with background fill.
     *
     * @return initialized inventory
     */
    private static Inventory createGUI() {

        Inventory gui = Bukkit.createInventory(null, 54, TITLE);
        fill(gui);
        return gui;
    }

    /**
     * Resolves an item from:
     * - custom item registry
     * - vanilla material fallback
     *
     * @param id item id or material name
     * @return resolved item or null if invalid
     */
    private static ItemStack resolveItem(String id) {

        if (id == null) return null;

        ItemStack item = ItemRegistry.get(id);
        if (item != null) return item;

        Material mat = Material.matchMaterial(id);
        return mat != null ? new ItemStack(mat) : null;
    }

    /**
     * Creates a furnace icon based on recipe type.
     *
     * @param type furnace type (furnace, blast, smoker)
     * @return display item
     */
    private static ItemStack getFurnace(String type) {

        Material mat = switch (type.toLowerCase()) {
            case "blast" -> Material.BLAST_FURNACE;
            case "smoker" -> Material.SMOKER;
            default -> Material.FURNACE;
        };

        return createItem(mat, "§7Smelting");
    }

    /*
     * =========================
     * UI HELPERS
     * =========================
     */

    /**
     * Fills the GUI with background panes.
     */
    private static void fill(Inventory gui) {

        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    /**
     * Creates an empty placeholder slot.
     */
    private static ItemStack emptySlot() {
        return createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ");
    }

    /**
     * Creates a simple named item.
     *
     * @param material item material
     * @param name     display name
     * @return created item
     */
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