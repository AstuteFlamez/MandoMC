package com.bilal.mandomc.features.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import com.bilal.mandomc.features.items.ItemRegistry;
import com.bilal.mandomc.features.items.RecipeRegistry;

public class RecipeViewerGUI {

    private static final String TITLE = "§8Recipe Viewer";

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
     * Crafting Recipe GUI
     */
    private static void openCrafting(Player player, String id) {

        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        fillBackground(gui);

        RecipeRegistry.CraftingRecipeData data = RecipeRegistry.getCrafting(id);

        int[] grid = {10,11,12,19,20,21,28,29,30};

        int index = 0;

        for (String row : data.shape) {

            for (char c : row.toCharArray()) {

                String ingredient = data.ingredients.get(c);

                ItemStack stack = null;

                if (ingredient != null) {

                    stack = ItemRegistry.get(ingredient);

                    if (stack == null) {

                        Material mat = Material.matchMaterial(ingredient);

                        if (mat != null)
                            stack = new ItemStack(mat);
                    }
                }

                if (stack != null)
                    gui.setItem(grid[index], stack);
                else
                    gui.setItem(grid[index], emptySlot());

                index++;
            }
        }

        gui.setItem(23, craftingTable());

        gui.setItem(25, ItemRegistry.get(id));

        gui.setItem(53, back());

        player.openInventory(gui);
    }

    /*
     * Smelting Recipe GUI
     */
    private static void openSmelting(Player player, String id) {

        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        fillBackground(gui);

        RecipeRegistry.SmeltingRecipeData data = RecipeRegistry.getSmelting(id);

        ItemStack input = ItemRegistry.get(data.input);

        if (input == null) {

            Material mat = Material.matchMaterial(data.input);

            if (mat != null)
                input = new ItemStack(mat);
        }

        gui.setItem(20, input);

        gui.setItem(22, furnace(data.furnace));

        gui.setItem(24, ItemRegistry.get(id));

        gui.setItem(53, back());

        player.openInventory(gui);
    }

    /*
     * Background filler
     */
    private static void fillBackground(Inventory gui) {

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    /*
     * Empty crafting slot
     */
    private static ItemStack emptySlot() {

        ItemStack pane = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);

        return pane;
    }

    /*
     * Crafting table icon
     */
    private static ItemStack craftingTable() {

        ItemStack table = new ItemStack(Material.CRAFTING_TABLE);

        ItemMeta meta = table.getItemMeta();
        meta.setDisplayName("§7Crafting");
        table.setItemMeta(meta);

        return table;
    }

    /*
     * Furnace icon
     */
    private static ItemStack furnace(String type) {

        Material mat = Material.FURNACE;

        if (type.equalsIgnoreCase("blast"))
            mat = Material.BLAST_FURNACE;

        if (type.equalsIgnoreCase("smoker"))
            mat = Material.SMOKER;

        ItemStack furnace = new ItemStack(mat);

        ItemMeta meta = furnace.getItemMeta();
        meta.setDisplayName("§7Smelting");
        furnace.setItemMeta(meta);

        return furnace;
    }

    /*
     * Back button
     */
    private static ItemStack back() {

        ItemStack arrow = new ItemStack(Material.ARROW);

        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName("§cBack");
        arrow.setItemMeta(meta);

        return arrow;
    }
}