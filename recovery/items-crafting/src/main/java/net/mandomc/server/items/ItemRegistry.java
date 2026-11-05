package net.mandomc.server.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import net.mandomc.MandoMC;
import net.mandomc.server.items.config.ItemsConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for all loaded items.
 *
 * Supports full reload by clearing and rebuilding from config.
 */
public final class ItemRegistry {

    private static final Map<String, ItemStack> items = new HashMap<>();
    private static final Map<String, String> categories = new HashMap<>();
    private static final Map<String, String> rarities = new HashMap<>();
    private static final Map<String, Set<String>> tags = new HashMap<>();

    /**
     * Registers an item with its metadata.
     *
     * @param id       the item identifier
     * @param item     the ItemStack to register
     * @param category the category name
     * @param rarity   the rarity name
     * @param itemTags the list of tags for this item
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
     * Clears and rebuilds the registry from all loaded item configs.
     */
    public static void reload() {

        clear();

        int loaded = 0;

        for (FileConfiguration config : ItemsConfig.getAll().values()) {

            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection == null) continue;

            for (String id : itemsSection.getKeys(false)) {

                ConfigurationSection section = itemsSection.getConfigurationSection(id);
                if (section == null) continue;

                try {

                    ItemStack item = ItemFactory.createItem(id, section);

                    String category = section.getString("category", "misc");
                    String rarity = section.getString("rarity", "common");
                    List<String> itemTags = section.getStringList("tags");

                    register(id, item, category, rarity, itemTags);
                    loaded++;

                } catch (Exception e) {

                    MandoMC.getInstance().getLogger()
                            .severe("Failed to load item: " + id);
                    e.printStackTrace();
                }
            }
        }

        MandoMC.getInstance().getLogger()
                .info("ItemRegistry loaded " + loaded + " items.");
    }

    /**
     * Clears all registered items and their metadata.
     */
    public static void clear() {
        items.clear();
        categories.clear();
        rarities.clear();
        tags.clear();
    }

    /**
     * Returns a clone of the registered item, or null if not found.
     *
     * @param id the item identifier
     * @return a cloned ItemStack, or null
     */
    public static ItemStack get(String id) {
        ItemStack item = items.get(normalizeId(id));
        return item == null ? null : item.clone();
    }

    /**
     * Returns all registered item IDs.
     *
     * @return the set of item IDs
     */
    public static Set<String> getItemIds() {
        return items.keySet();
    }

    /**
     * Returns the category for the given item ID.
     *
     * @param id the item identifier
     * @return the category string
     */
    public static String getCategory(String id) {
        return categories.get(normalizeId(id));
    }

    /**
     * Returns the rarity for the given item ID, defaulting to "common".
     *
     * @param id the item identifier
     * @return the rarity string
     */
    public static String getRarity(String id) {
        return rarities.getOrDefault(normalizeId(id), "common");
    }

    /**
     * Returns the tags associated with the given item ID.
     *
     * @param id the item identifier
     * @return a set of tag strings (uppercase)
     */
    public static Set<String> getTags(String id) {
        return tags.getOrDefault(normalizeId(id), Collections.emptySet());
    }

    /**
     * Returns true if the item has the specified tag.
     *
     * @param id  the item identifier
     * @param tag the tag to check (case-insensitive)
     * @return true if the item has the tag
     */
    public static boolean hasTag(String id, String tag) {
        return getTags(id).contains(tag.toUpperCase());
    }

    /**
     * Returns the distinct set of all registered category values.
     *
     * @return a set of category strings
     */
    public static Set<String> getCategories() {
        return new HashSet<>(categories.values());
    }

    private static String normalizeId(String id) {
        return id.toLowerCase();
    }

    private static Set<String> normalizeTags(List<String> itemTags) {

        Set<String> tagSet = new HashSet<>();

        if (itemTags != null) {
            for (String tag : itemTags) {
                tagSet.add(tag.toUpperCase());
            }
        }

        return tagSet;
    }

    private ItemRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}
