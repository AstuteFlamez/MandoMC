package net.mandomc.system.items.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;

import net.mandomc.MandoMC;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.RecipeRegistry;
import net.mandomc.system.items.guis.RecipeBrowserGUI;
import net.mandomc.system.items.guis.RecipeViewerGUI;

import java.util.Map;

/**
 * Handles all recipe GUI interactions.
 *
 * Supports:
 * - Category navigation
 * - Back button routing
 * - Opening recipe viewers
 */
public class RecipeListener implements Listener {

    private static final String ROOT_TITLE = "§8Recipes";
    private static final String VIEWER_TITLE = "§8Recipe Viewer";

    private static final Map<String, RunnableCategory> CATEGORY_ACTIONS = Map.of(
            "§6§lMetals", p -> openCategory(p, "metals"),
            "§6§lArmor", p -> openCategory(p, "armor"),
            "§6§lHilts", p -> openCategory(p, "hilts"),
            "§6§lSabers", p -> openCategory(p, "sabers"),
            "§6§lFuel", p -> openCategory(p, "fuel"),
            "§6§lComponents", p -> openCategory(p, "components"),
            "§6§lVehicles", p -> openCategory(p, "vehicles")
    );

    /**
     * Handles click interactions within recipe GUIs.
     *
     * Cancels interaction and routes clicks to:
     * - back navigation
     * - category selection
     * - recipe viewing
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        if (!isRecipeGUI(title)) return;

        event.setCancelled(true);

        if (!isValidItem(event.getCurrentItem())) return;

        String name = event.getCurrentItem().getItemMeta().getDisplayName();

        if (handleBack(player, title, name)) return;

        if (title.equals(ROOT_TITLE)) {
            handleCategoryClick(player, name);
            return;
        }

        handleRecipeOpen(player, event.getCurrentItem());
    }

    /**
     * Prevents dragging items inside recipe GUIs.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {

        if (isRecipeGUI(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * HANDLERS
     * =========================
     */

    /**
     * Handles back button navigation.
     *
     * Behavior:
     * - From viewer → returns to last category if available
     * - Otherwise → returns to root menu
     *
     * @param player the viewer
     * @param title  current GUI title
     * @param name   clicked item name
     * @return true if back action was handled
     */
    private boolean handleBack(Player player, String title, String name) {

        if (!name.equalsIgnoreCase("§cBack")) return false;

        if (title.equals(VIEWER_TITLE) && player.hasMetadata("last_category")) {

            String category = player.getMetadata("last_category").get(0).asString();
            openCategory(player, category);

        } else {
            RecipeBrowserGUI.openRoot(player);
        }

        return true;
    }

    /**
     * Handles category selection from the root menu.
     *
     * @param player the viewer
     * @param name   clicked display name
     */
    private void handleCategoryClick(Player player, String name) {

        RunnableCategory action = CATEGORY_ACTIONS.get(name);
        if (action != null) {
            action.open(player);
        }
    }

    /**
     * Opens a recipe viewer for the selected item.
     *
     * @param player the viewer
     * @param item   the clicked item
     */
    private void handleRecipeOpen(Player player, org.bukkit.inventory.ItemStack item) {

        String id = ItemUtils.getItemId(item);

        if (id == null) return;
        if (!RecipeRegistry.hasRecipe(id)) return;

        RecipeViewerGUI.open(player, id);
    }

    /*
     * =========================
     * CATEGORY ROUTING
     * =========================
     */

    /**
     * Opens a category GUI and stores it as the last visited category.
     *
     * @param player   the viewer
     * @param category category key
     */
    private static void openCategory(Player player, String category) {

        setCategory(player, category);

        switch (category) {
            case "metals" -> RecipeBrowserGUI.openMetals(player);
            case "armor" -> RecipeBrowserGUI.openArmor(player);
            case "hilts" -> RecipeBrowserGUI.openHilts(player);
            case "sabers" -> RecipeBrowserGUI.openSabers(player);
            case "components" -> RecipeBrowserGUI.openComponents(player);
            case "fuel" -> RecipeBrowserGUI.openFuel(player);
            case "vehicles" -> RecipeBrowserGUI.openVehicles(player);
            default -> RecipeBrowserGUI.openRoot(player);
        }
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Stores the last visited category in player metadata.
     *
     * @param player   the viewer
     * @param category category key
     */
    private static void setCategory(Player player, String category) {
        player.setMetadata("last_category",
                new FixedMetadataValue(MandoMC.getInstance(), category));
    }

    /**
     * Checks if a GUI title belongs to the recipe system.
     *
     * @param title the inventory title
     * @return true if this is a recipe GUI
     */
    private boolean isRecipeGUI(String title) {
        return title.contains("Recipes") || title.equals(VIEWER_TITLE);
    }

    /**
     * Validates that an item can be safely interacted with.
     *
     * @param item the clicked item
     * @return true if valid
     */
    private boolean isValidItem(org.bukkit.inventory.ItemStack item) {
        return item != null && item.hasItemMeta();
    }

    /*
     * =========================
     * FUNCTIONAL INTERFACE
     * =========================
     */

    /**
     * Represents a category action that opens a GUI.
     */
    @FunctionalInterface
    private interface RunnableCategory {

        /**
         * Executes the category open action.
         *
         * @param player the viewer
         */
        void open(Player player);
    }
}