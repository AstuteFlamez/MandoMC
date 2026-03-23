package com.astuteflamez.mandomc.features.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.features.items.ItemRegistry;

public class RecipeBrowserGUI {

    /*
     * =========================
     * MAIN PAGE
     * =========================
     */
    public static void openRoot(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes");

        fill(gui);

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
     * METALS
     * =========================
     */
    public static void openMetals(Player player) {

        Inventory gui = Bukkit.createInventory(null, 54, "§8Recipes » §6§lMetals");

        fill(gui);

        gui.setItem(10, ItemRegistry.get("agrinium"));
        gui.setItem(11, ItemRegistry.get("agrinium_sheet"));
        gui.setItem(12, ItemRegistry.get("aurodium"));
        gui.setItem(13, ItemRegistry.get("aurodium_sheet"));
        gui.setItem(14, ItemRegistry.get("beskar"));
        gui.setItem(15, ItemRegistry.get("beskar_sheet"));

        gui.setItem(19, ItemRegistry.get("cortosis"));
        gui.setItem(20, ItemRegistry.get("cortosis_sheet"));
        gui.setItem(21, ItemRegistry.get("durasteel"));
        gui.setItem(22, ItemRegistry.get("durasteel_sheet"));
        gui.setItem(23, ItemRegistry.get("electrum"));
        gui.setItem(24, ItemRegistry.get("electrum_sheet"));

        gui.setItem(28, ItemRegistry.get("lommite"));
        gui.setItem(29, ItemRegistry.get("lommite_sheet"));
        gui.setItem(30, ItemRegistry.get("mandalorian_iron"));
        gui.setItem(31, ItemRegistry.get("mandalorian_sheet"));
        gui.setItem(32, ItemRegistry.get("neuranium"));
        gui.setItem(33, ItemRegistry.get("neuranium_sheet"));

        gui.setItem(37, ItemRegistry.get("phrik"));
        gui.setItem(38, ItemRegistry.get("phrik_sheet"));
        gui.setItem(39, ItemRegistry.get("songsteel"));
        gui.setItem(40, ItemRegistry.get("songsteel_sheet"));

        gui.setItem(53, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * ARMOR
     * =========================
     */
    public static void openArmor(Player player) {

        Inventory gui = Bukkit.createInventory(null, 36, "§8Recipes » §6§lArmor");

        fill(gui);

        gui.setItem(10, ItemRegistry.get("plastoid_helmet"));
        gui.setItem(12, ItemRegistry.get("plastoid_chestplate"));
        gui.setItem(14, ItemRegistry.get("plastoid_leggings"));
        gui.setItem(16, ItemRegistry.get("plastoid_boots"));
        gui.setItem(19, ItemRegistry.get("beskar_helmet"));
        gui.setItem(21, ItemRegistry.get("beskar_chestplate"));
        gui.setItem(23, ItemRegistry.get("beskar_leggings"));
        gui.setItem(25, ItemRegistry.get("beskar_boots"));

        gui.setItem(35, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * HILTS
     * =========================
     */
    public static void openHilts(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes » §6§lHilts");

        fill(gui);

        gui.setItem(11, ItemRegistry.get("straight_hilt"));
        gui.setItem(12, ItemRegistry.get("great_hilt"));
        gui.setItem(13, ItemRegistry.get("whip_hilt"));
        gui.setItem(14, ItemRegistry.get("pike_hilt"));
        gui.setItem(15, ItemRegistry.get("fang_hilt"));

        gui.setItem(26, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * SABERS
     * =========================
     */
    public static void openSabers(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes » §6§lSabers");

        fill(gui);

        gui.setItem(11, ItemRegistry.get("obiwan"));
        gui.setItem(12, ItemRegistry.get("cal_survivor_crossguard"));
        gui.setItem(13, ItemRegistry.get("windu"));
        gui.setItem(14, ItemRegistry.get("quigon"));
        gui.setItem(15, ItemRegistry.get("ventress_yellow"));

        gui.setItem(26, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * COMPONENTS
     * =========================
     */
    public static void openComponents(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes » §6§lComponents");

        fill(gui);

        gui.setItem(11, ItemRegistry.get("tiefighter_cockpit"));
        gui.setItem(12, ItemRegistry.get("tiefighter_wing"));
        gui.setItem(13, ItemRegistry.get("x_wing_cockpit"));
        gui.setItem(14, ItemRegistry.get("x_wing_left_wing"));
        gui.setItem(15, ItemRegistry.get("x_wing_right_wing"));

        gui.setItem(26, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * FUEL
     * =========================
     */
    public static void openFuel(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes » §6§lFuel");

        fill(gui);

        gui.setItem(12, ItemRegistry.get("rhydonium_canister"));
        gui.setItem(14, ItemRegistry.get("rhydonium_barrel"));

        gui.setItem(26, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * VEHICLES
     * =========================
     */
    public static void openVehicles(Player player) {

        Inventory gui = Bukkit.createInventory(null, 27, "§8Recipes » §6§lVehicles");

        fill(gui);

        gui.setItem(12, ItemRegistry.get("tiefighter"));
        gui.setItem(14, ItemRegistry.get("xwing"));

        gui.setItem(26, button("§cBack"));

        player.openInventory(gui);
    }

    /*
     * =========================
     * HELPERS
     * =========================
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

    private static ItemStack button(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void fill(Inventory gui) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }
}