package net.mandomc.system.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import net.mandomc.MandoMC;
import net.mandomc.system.items.config.ItemsConfig;

import java.util.*;

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

    /* =====================================================
       REGISTER
    ===================================================== */

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

    /* =====================================================
       RELOAD (🔥 THIS IS WHAT YOU NEEDED)
    ===================================================== */

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

    /* =====================================================
       CLEAR
    ===================================================== */

    public static void clear() {
        items.clear();
        categories.clear();
        rarities.clear();
        tags.clear();
    }

    /* =====================================================
       GETTERS
    ===================================================== */

    public static ItemStack get(String id) {
        ItemStack item = items.get(normalizeId(id));
        return item == null ? null : item.clone();
    }

    public static Set<String> getItemIds() {
        return items.keySet();
    }

    public static String getCategory(String id) {
        return categories.get(normalizeId(id));
    }

    public static String getRarity(String id) {
        return rarities.getOrDefault(normalizeId(id), "common");
    }

    public static Set<String> getTags(String id) {
        return tags.getOrDefault(normalizeId(id), Collections.emptySet());
    }

    public static boolean hasTag(String id, String tag) {
        return getTags(id).contains(tag.toUpperCase());
    }

    public static Set<String> getCategories() {
        return new HashSet<>(categories.values());
    }

    /* =====================================================
       HELPERS
    ===================================================== */

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