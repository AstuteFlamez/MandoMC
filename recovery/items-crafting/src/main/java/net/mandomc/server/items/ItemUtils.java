package net.mandomc.server.items;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for working with custom items.
 *
 * Provides:
 * - Item id retrieval
 * - Tag handling
 * - Custom item checks
 */
public final class ItemUtils {

    /**
     * Retrieves the custom item id from an ItemStack.
     *
     * @param item the item
     * @return item id or null if not a custom item
     */
    public static String getItemId(ItemStack item) {

        ItemMeta meta = getMeta(item);
        if (meta == null) return null;

        return meta.getPersistentDataContainer()
                .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
    }

    /**
     * Checks whether an item is a custom item.
     *
     * @param item the item
     * @return true if the item has a custom id
     */
    public static boolean isCustomItem(ItemStack item) {
        return getItemId(item) != null;
    }

    /**
     * Checks whether an item has a specific tag.
     *
     * @param item the item
     * @param tag  tag to check
     * @return true if the tag exists
     */
    public static boolean hasTag(ItemStack item, String tag) {

        for (String t : getTags(item)) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Retrieves all tags associated with an item.
     *
     * @param item the item
     * @return list of tags (empty if none)
     */
    public static List<String> getTags(ItemStack item) {

        ItemMeta meta = getMeta(item);
        if (meta == null) return Collections.emptyList();

        String tags = meta.getPersistentDataContainer()
                .get(ItemKeys.ITEM_TAGS, PersistentDataType.STRING);

        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        for (String tag : tags.split(",")) {
            result.add(tag.trim());
        }

        return result;
    }

    /**
     * Checks whether an item matches a specific id.
     *
     * @param item the item
     * @param id   expected item id
     * @return true if the ids match
     */
    public static boolean isItem(ItemStack item, String id) {

        String itemId = getItemId(item);
        return itemId != null && itemId.equalsIgnoreCase(id);
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Safely retrieves item meta.
     *
     * @param item the item
     * @return item meta or null if unavailable
     */
    private static ItemMeta getMeta(ItemStack item) {

        if (item == null) return null;
        return item.getItemMeta();
    }

    /**
     * Prevent instantiation.
     */
    private ItemUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}