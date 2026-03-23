package com.astuteflamez.mandomc.system.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.system.items.ItemRegistry;

import java.util.List;

/**
 * Handles all recipe browsing GUIs.
 *
 * Provides:
 * - Root category menu
 * - Category-based recipe lists
 * - Shared layout builder for all recipe views
 */
public class RecipeBrowserGUI {

    private static final String ROOT_TITLE = "§8Recipes";

    /*
     * =========================
     * ROOT MENU
     * =========================
     */

    /**
     * Opens the root recipe menu.
     *
     * Displays all available recipe categories.
     *
     * @param player the viewer
     */
    public static void openRoot(Player player) {

        Inventory gui = createGUI(27, ROOT_TITLE);

        gui.setItem(10, category("§6§lMetals", "agrinium"));
        gui.setItem(11, category("§6§lArmor", "plastoid_helmet"));
        gui.setItem(12, category("§6§lHilts", "straight_hilt"));
        gui.setItem(13, category("§6§lSabers", "windu"));
        gui.setItem(14, category("§6§lFuel", "rhydonium_canister"));
        gui.setItem(15, category("§6§lComponents", "tiefighter_cockpit"));
        gui.setItem(16, category("§6§lVehicles", "tiefighter"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * CATEGORY SCREENS
     * =========================
     */

    /**
     * Opens the metals recipe category.
     */
    public static void openMetals(Player player) {
        openCategory(player, "§8Recipes » §6§lMetals", 54, List.of(
                "agrinium","agrinium_sheet","aurodium","aurodium_sheet","beskar","beskar_sheet",
                "cortosis","cortosis_sheet","durasteel","durasteel_sheet","electrum","electrum_sheet",
                "lommite","lommite_sheet","mandalorian_iron","mandalorian_sheet","neuranium","neuranium_sheet",
                "phrik","phrik_sheet","songsteel","songsteel_sheet"
        ));
    }

    /**
     * Opens the armor recipe category.
     */
    public static void openArmor(Player player) {
        openCategory(player, "§8Recipes » §6§lArmor", 36, List.of(
                "plastoid_helmet","plastoid_chestplate","plastoid_leggings","plastoid_boots",
                "beskar_helmet","beskar_chestplate","beskar_leggings","beskar_boots"
        ));
    }

    /**
     * Opens the hilts recipe category.
     */
    public static void openHilts(Player player) {
        openCategory(player, "§8Recipes » §6§lHilts", 27,
                List.of("straight_hilt","great_hilt","whip_hilt","pike_hilt","fang_hilt"));
    }

    /**
     * Opens the sabers recipe category.
     */
    public static void openSabers(Player player) {
        openCategory(player, "§8Recipes » §6§lSabers", 27,
                List.of("obiwan","cal_survivor_crossguard","windu","quigon","ventress_yellow"));
    }

    /**
     * Opens the components recipe category.
     */
    public static void openComponents(Player player) {
        openCategory(player, "§8Recipes » §6§lComponents", 27, List.of(
                "tiefighter_cockpit","tiefighter_wing",
                "x_wing_cockpit","x_wing_left_wing","x_wing_right_wing"
        ));
    }

    /**
     * Opens the fuel recipe category.
     */
    public static void openFuel(Player player) {
        openCategory(player, "§8Recipes » §6§lFuel", 27,
                List.of("rhydonium_canister","rhydonium_barrel"));
    }

    /**
     * Opens the vehicles recipe category.
     */
    public static void openVehicles(Player player) {
        openCategory(player, "§8Recipes » §6§lVehicles", 27,
                List.of("tiefighter","xwing"));
    }

    /*
     * =========================
     * CORE BUILDER
     * =========================
     */

    /**
     * Opens a category GUI using a standard layout.
     *
     * Layout rules:
     * - Items start at slot 10
     * - Last column of each row is skipped
     * - Back button is placed in final slot
     *
     * @param player  the viewer
     * @param title   GUI title
     * @param size    inventory size
     * @param itemIds item ids to display
     */
    private static void openCategory(Player player, String title, int size, List<String> itemIds) {

        Inventory gui = createGUI(size, title);

        int slot = 10;

        for (String id : itemIds) {

            ItemStack item = ItemRegistry.get(id);
            if (item != null) {
                gui.setItem(slot, item);
            }

            slot++;

            // Skip edge columns
            if (slot % 9 == 0) slot++;
        }

        gui.setItem(size - 1, button("§cBack"));

        player.openInventory(gui);
    }

    /**
     * Creates a GUI with a filled background.
     *
     * @param size  inventory size
     * @param title GUI title
     * @return created inventory
     */
    private static Inventory createGUI(int size, String title) {

        Inventory gui = Bukkit.createInventory(null, size, title);
        fill(gui);
        return gui;
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Creates a category icon based on an existing item.
     *
     * @param name   display name
     * @param iconId item id used as icon
     * @return display item
     */
    private static ItemStack category(String name, String iconId) {

        ItemStack item = ItemRegistry.get(iconId);
        if (item == null) item = new ItemStack(Material.BARRIER);

        ItemStack display = item.clone();

        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            display.setItemMeta(meta);
        }

        return display;
    }

    /**
     * Creates a navigation button.
     */
    private static ItemStack button(String name) {
        return createItem(Material.ARROW, name);
    }

    /**
     * Fills inventory with background panes.
     */
    private static void fill(Inventory gui) {

        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    /**
     * Creates a simple named item.
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