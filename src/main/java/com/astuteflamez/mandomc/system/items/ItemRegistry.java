package com.astuteflamez.mandomc.system.items;

import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Central registry for all loaded items.
 *
 * Stores:
 * - Item instances
 * - Categories
 * - Rarities
 * - Tags
 *
 * All item ids are normalized to lowercase.
 * All tags are normalized to uppercase.
 */
public final class ItemRegistry {

    private static final Map<String, ItemStack> items = new HashMap<>();
    private static final Map<String, String> categories = new HashMap<>();
    private static final Map<String, String> rarities = new HashMap<>();
    private static final Map<String, Set<String>> tags = new HashMap<>();

    /**
     * Registers an item and its metadata.
     *
     * @param id       unique item id
     * @param item     item stack instance
     * @param category item category
     * @param rarity   item rarity
     * @param itemTags list of tags associated with the item
     */
    public static void register(
            String id,
            ItemStack item,
            String category,
            String rarity,
            List<String> itemTags
    ) {
        id = normalizeId(id);

        items.put(id, item);
        categories.put(id, category);
        rarities.put(id, rarity);

        tags.put(id, normalizeTags(itemTags));
    }

    /**
     * Retrieves a cloned item by id.
     *
     * @param id item id
     * @return cloned ItemStack or null if not found
     */
    public static ItemStack get(String id) {
        ItemStack item = items.get(normalizeId(id));
        return item == null ? null : item.clone();
    }

    /**
     * Returns all registered item ids.
     *
     * @return set of item ids
     */
    public static Set<String> getItemIds() {
        return items.keySet();
    }

    /**
     * Retrieves the category of an item.
     *
     * @param id item id
     * @return category or null if not found
     */
    public static String getCategory(String id) {
        return categories.get(normalizeId(id));
    }

    /**
     * Retrieves the rarity of an item.
     *
     * @param id item id
     * @return rarity (defaults to "common" if missing)
     */
    public static String getRarity(String id) {
        return rarities.getOrDefault(normalizeId(id), "common");
    }

    /**
     * Retrieves all tags for an item.
     *
     * @param id item id
     * @return set of tags (empty if none)
     */
    public static Set<String> getTags(String id) {
        return tags.getOrDefault(normalizeId(id), Collections.emptySet());
    }

    /**
     * Checks if an item has a specific tag.
     *
     * @param id  item id
     * @param tag tag to check
     * @return true if item contains the tag
     */
    public static boolean hasTag(String id, String tag) {
        return getTags(id).contains(tag.toUpperCase());
    }

    /**
     * Retrieves all unique categories.
     *
     * @return set of categories
     */
    public static Set<String> getCategories() {
        return new HashSet<>(categories.values());
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Normalizes item ids to lowercase.
     *
     * @param id raw id
     * @return normalized id
     */
    private static String normalizeId(String id) {
        return id.toLowerCase();
    }

    /**
     * Normalizes tags to uppercase.
     *
     * @param itemTags raw tag list
     * @return normalized tag set
     */
    private static Set<String> normalizeTags(List<String> itemTags) {

        Set<String> tagSet = new HashSet<>();

        if (itemTags != null) {
            for (String tag : itemTags) {
                tagSet.add(tag.toUpperCase());
            }
        }

        return tagSet;
    }

    /**
     * Prevent instantiation.
     */
    private ItemRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}