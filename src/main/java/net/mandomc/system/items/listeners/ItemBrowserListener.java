package net.mandomc.system.items.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.guis.ItemBrowserGUI;

import java.util.UUID;

/**
 * Handles interaction with the Item Browser GUI.
 *
 * Supports:
 * - Category switching
 * - Pagination
 * - Giving items to a target player
 */
public class ItemBrowserListener implements Listener {

    private static final String TITLE = "§8Item Browser";

    /**
     * Handles click interactions inside the item browser GUI.
     *
     * Cancels all interaction and routes clicks to:
     * - category selection
     * - pagination
     * - item giving
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isItemBrowser(event)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (!isValidItem(clicked)) return;

        String name = clicked.getItemMeta().getDisplayName();

        Player target = getTarget(player);
        String category = getCategory(player);
        int page = getPage(player);

        if (clicked.getType() == Material.PAPER) {
            handleCategoryClick(player, target, name);
            return;
        }

        if (handleNavigation(player, target, category, page, name)) return;

        handleGiveItem(player, target, clicked);
    }

    /**
     * Prevents dragging items inside the GUI.
     *
     * @param event the drag event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(TITLE)) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * HANDLERS
     * =========================
     */

    /**
     * Handles clicking a category tab.
     *
     * Resets pagination and applies the selected category filter.
     *
     * @param player the viewer
     * @param target the target player receiving items
     * @param name   the clicked display name
     */
    private void handleCategoryClick(Player player, Player target, String name) {

        String category = ChatColor.stripColor(name);
        ItemBrowserGUI.open(player, target, 0, category);
    }

    /**
     * Handles pagination controls.
     *
     * @param player   the viewer
     * @param target   the target player
     * @param category current category filter
     * @param page     current page index
     * @param name     clicked display name
     * @return true if navigation was handled
     */
    private boolean handleNavigation(Player player, Player target, String category, int page, String name) {

        if (name.equalsIgnoreCase("§aNext Page")) {
            ItemBrowserGUI.open(player, target, page + 1, category);
            return true;
        }

        if (name.equalsIgnoreCase("§cPrevious Page")) {
            ItemBrowserGUI.open(player, target, page - 1, category);
            return true;
        }

        return false;
    }

    /**
     * Gives the selected item to the target player.
     *
     * @param player the viewer issuing the action
     * @param target the player receiving the item
     * @param item   the clicked item
     */
    private void handleGiveItem(Player player, Player target, ItemStack item) {

        String id = ItemUtils.getItemId(item);

        if (id == null) return;
        if (!ItemRegistry.getItemIds().contains(id)) return;

        target.getInventory().addItem(item.clone());

        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aGave item to §f" + target.getName());
    }

    /*
     * =========================
     * STATE HELPERS
     * =========================
     */

    /**
     * Resolves the target player from metadata.
     *
     * Defaults to the viewer if metadata is missing or invalid.
     *
     * @param player the viewer
     * @return resolved target player
     */
    private Player getTarget(Player player) {

        if (!player.hasMetadata("item_browser_target")) return player;

        UUID id = (UUID) player.getMetadata("item_browser_target").get(0).value();
        Player target = Bukkit.getPlayer(id);

        return target != null ? target : player;
    }

    /**
     * Retrieves the current category filter.
     *
     * @param player the viewer
     * @return category or null if not set
     */
    private String getCategory(Player player) {

        if (!player.hasMetadata("item_browser_category")) return null;
        return player.getMetadata("item_browser_category").get(0).asString();
    }

    /**
     * Retrieves the current page index.
     *
     * @param player the viewer
     * @return page index (defaults to 0)
     */
    private int getPage(Player player) {

        if (!player.hasMetadata("item_browser_page")) return 0;
        return player.getMetadata("item_browser_page").get(0).asInt();
    }

    /*
     * =========================
     * VALIDATION
     * =========================
     */

    /**
     * Checks if the event belongs to the item browser GUI.
     *
     * @param event the click event
     * @return true if this GUI is the item browser
     */
    private boolean isItemBrowser(InventoryClickEvent event) {
        return event.getView().getTitle().equals(TITLE);
    }

    /**
     * Validates that an item is safe to interact with.
     *
     * @param item the clicked item
     * @return true if valid
     */
    private boolean isValidItem(ItemStack item) {
        return item != null && item.hasItemMeta();
    }
}