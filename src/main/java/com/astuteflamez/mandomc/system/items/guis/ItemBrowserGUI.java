package com.astuteflamez.mandomc.system.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.system.items.ItemRegistry;

import java.util.*;

/**
 * GUI for browsing all registered items.
 *
 * Features:
 * - Pagination support
 * - Category filtering
 * - Target player selection (for giving items)
 */
public class ItemBrowserGUI {

    private static final String TITLE = "§8Item Browser";
    private static final int SIZE = 54;
    private static final int ITEMS_PER_PAGE = 36;

    /**
     * Opens the item browser targeting the player themselves.
     *
     * @param player the viewer and target
     */
    public static void open(Player player) {
        open(player, player, 0, null);
    }

    /**
     * Opens the item browser targeting another player.
     *
     * @param viewer the player viewing the GUI
     * @param target the player receiving items
     */
    public static void open(Player viewer, Player target) {
        open(viewer, target, 0, null);
    }

    /**
     * Opens the item browser with full control.
     *
     * @param viewer   the player viewing the GUI
     * @param target   the player receiving items
     * @param page     the page index (0-based)
     * @param category the selected category (null for all)
     */
    public static void open(Player viewer, Player target, int page, String category) {

        Inventory gui = Bukkit.createInventory(null, SIZE, TITLE);

        fillBackground(gui);

        List<String> categories = getCategories();
        placeCategoryTabs(gui, categories, category);

        List<String> items = getItems(category);

        page = clampPage(page, items.size());
        applyMetadata(viewer, target, page, category);

        placeItems(gui, items, page);
        placeNavigation(gui, page, items.size());

        viewer.openInventory(gui);
    }

    /**
     * Retrieves all categories sorted alphabetically.
     *
     * @return sorted list of categories
     */
    private static List<String> getCategories() {
        List<String> categories = new ArrayList<>(ItemRegistry.getCategories());
        Collections.sort(categories);
        return categories;
    }

    /**
     * Retrieves item ids filtered by category.
     *
     * @param category the category filter, or null for all items
     * @return sorted list of item ids
     */
    private static List<String> getItems(String category) {

        List<String> ids = new ArrayList<>();

        for (String id : ItemRegistry.getItemIds()) {
            if (category == null || category.equals(ItemRegistry.getCategory(id))) {
                ids.add(id);
            }
        }

        Collections.sort(ids);
        return ids;
    }

    /**
     * Clamps a page value to a valid range.
     *
     * @param page       requested page
     * @param totalItems total number of items
     * @return valid page index
     */
    private static int clampPage(int page, int totalItems) {

        int maxPage = totalItems == 0 ? 0 : (totalItems - 1) / ITEMS_PER_PAGE;

        if (page > maxPage) return maxPage;
        if (page < 0) return 0;

        return page;
    }

    /**
     * Stores GUI state in player metadata.
     *
     * Used for:
     * - target player tracking
     * - pagination
     * - category filtering
     *
     * @param viewer   the player viewing the GUI
     * @param target   the target player
     * @param page     current page
     * @param category selected category
     */
    private static void applyMetadata(Player viewer, Player target, int page, String category) {

        viewer.setMetadata("item_browser_target",
                new FixedMetadataValue(MandoMC.getInstance(), target.getUniqueId()));

        viewer.setMetadata("item_browser_page",
                new FixedMetadataValue(MandoMC.getInstance(), page));

        viewer.setMetadata("item_browser_category",
                new FixedMetadataValue(MandoMC.getInstance(), category));
    }

    /**
     * Places items into the GUI grid.
     *
     * Layout:
     * - Starts at slot 9
     * - Skips last column of each row
     *
     * @param gui   the inventory
     * @param items list of item ids
     * @param page  current page
     */
    private static void placeItems(Inventory gui, List<String> items, int page) {

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        int slot = 9;

        for (int i = start; i < end; i++) {

            ItemStack item = ItemRegistry.get(items.get(i));
            if (item != null) {
                gui.setItem(slot, item);
            }

            slot++;

            // Skip last column
            if (slot % 9 == 0) slot++;
        }
    }

    /**
     * Fills the GUI with background panes.
     *
     * @param gui the inventory
     */
    private static void fillBackground(Inventory gui) {

        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    /**
     * Places category tabs in the top row.
     *
     * @param gui        the inventory
     * @param categories available categories
     * @param selected   currently selected category
     */
    private static void placeCategoryTabs(Inventory gui, List<String> categories, String selected) {

        int slot = 0;

        for (String category : categories) {

            String name = category.equals(selected)
                    ? "§a" + category
                    : "§e" + category;

            gui.setItem(slot, createItem(Material.PAPER, name));

            slot++;
            if (slot >= 9) break;
        }
    }

    /**
     * Places navigation buttons (next/previous page).
     *
     * @param gui        the inventory
     * @param page       current page
     * @param totalItems total items available
     */
    private static void placeNavigation(Inventory gui, int page, int totalItems) {

        if ((page + 1) * ITEMS_PER_PAGE < totalItems) {
            gui.setItem(53, createItem(Material.ARROW, "§aNext Page"));
        }

        if (page > 0) {
            gui.setItem(45, createItem(Material.ARROW, "§cPrevious Page"));
        }
    }

    /**
     * Creates a simple item with a display name.
     *
     * @param material the item material
     * @param name     the display name
     * @return the created item
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