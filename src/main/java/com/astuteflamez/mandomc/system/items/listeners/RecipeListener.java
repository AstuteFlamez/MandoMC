package com.astuteflamez.mandomc.system.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.system.items.ItemUtils;
import com.astuteflamez.mandomc.system.items.RecipeRegistry;
import com.astuteflamez.mandomc.system.items.guis.RecipeBrowserGUI;
import com.astuteflamez.mandomc.system.items.guis.RecipeViewerGUI;

public class RecipeListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();

        /*
         * 🔥 HANDLE BOTH GUIS
         */
        if (!title.contains("Recipes") && !title.equals("§8Recipe Viewer")) return;

        e.setCancelled(true); // 🔥 ALWAYS cancel

        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        String name = e.getCurrentItem().getItemMeta().getDisplayName();

        /*
         * =========================
         * BACK BUTTON (GLOBAL FIX)
         * =========================
         */
        if (name.equalsIgnoreCase("§cBack")) {

            // 🔥 If coming from viewer → go back to category
            if (title.equals("§8Recipe Viewer") && player.hasMetadata("last_category")) {

                String category = player.getMetadata("last_category").get(0).asString();

                switch (category.toLowerCase()) {
                    case "metals" -> RecipeBrowserGUI.openMetals(player);
                    case "armor" -> RecipeBrowserGUI.openArmor(player);
                    case "hilts" -> RecipeBrowserGUI.openHilts(player);
                    case "sabers" -> RecipeBrowserGUI.openSabers(player);
                    case "components" -> RecipeBrowserGUI.openComponents(player);
                    case "fuel" -> RecipeBrowserGUI.openFuel(player);
                    case "vehicles" -> RecipeBrowserGUI.openVehicles(player);
                    default -> RecipeBrowserGUI.openRoot(player);
                }

                return;
            }

            // Otherwise go to root
            RecipeBrowserGUI.openRoot(player);
            return;
        }

        /*
         * =========================
         * MAIN PAGE
         * =========================
         */
        if (title.equals("§8Recipes")) {

            switch (name) {
                case "§6§lMetals" -> {
                    setCategory(player, "metals");
                    RecipeBrowserGUI.openMetals(player);
                }
                case "§6§lArmor" -> {
                    setCategory(player, "armor");
                    RecipeBrowserGUI.openArmor(player);
                }
                case "§6§lHilts" -> {
                    setCategory(player, "hilts");
                    RecipeBrowserGUI.openHilts(player);
                }
                case "§6§lSabers" -> {
                    setCategory(player, "sabers");
                    RecipeBrowserGUI.openSabers(player);
                }
                case "§6§lFuel" -> {
                    setCategory(player, "fuel");
                    RecipeBrowserGUI.openFuel(player);
                }
                case "§6§lComponents" -> {
                    setCategory(player, "components");
                    RecipeBrowserGUI.openComponents(player);
                }
                case "§6§lVehicles" -> {
                    setCategory(player, "vehicles");
                    RecipeBrowserGUI.openVehicles(player);
                }
            }

            return;
        }

        /*
         * =========================
         * OPEN RECIPE
         * =========================
         */
        String id = ItemUtils.getItemId(e.getCurrentItem());

        if (id == null) return;
        if (!RecipeRegistry.hasRecipe(id)) return;

        RecipeViewerGUI.open(player, id);
    }

    /*
     * =========================
     * PREVENT DRAG (FIXED)
     * =========================
     */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {

        String title = e.getView().getTitle();

        if (title.contains("Recipes") || title.equals("§8Recipe Viewer")) {
            e.setCancelled(true);
        }
    }

    /*
     * =========================
     * HELPER
     * =========================
     */
    private void setCategory(Player player, String category) {
        player.setMetadata("last_category",
                new FixedMetadataValue(MandoMC.getInstance(), category));
    }
}