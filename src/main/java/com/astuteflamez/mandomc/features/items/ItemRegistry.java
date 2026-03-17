package com.astuteflamez.mandomc.features.items;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ItemRegistry {

    private static final Map<String, ItemStack> items = new HashMap<>();
    private static final Map<String, String> categories = new HashMap<>();
    private static final Map<String, String> rarities = new HashMap<>();
    private static final Map<String, Set<String>> tags = new HashMap<>();

    public static void register(
            String id,
            ItemStack item,
            String category,
            String rarity,
            List<String> itemTags
    ) {
        id = id.toLowerCase();

        items.put(id, item);
        categories.put(id, category);
        rarities.put(id, rarity);

        Set<String> tagSet = new HashSet<>();
        if (itemTags != null) {
            for (String tag : itemTags) {
                tagSet.add(tag.toUpperCase());
            }
        }

        tags.put(id, tagSet);
    }

    public static ItemStack get(String id) {
        ItemStack item = items.get(id.toLowerCase());
        return item == null ? null : item.clone();
    }

    public static Set<String> getItemIds() {
        return items.keySet();
    }

    public static String getCategory(String id) {
        return categories.get(id.toLowerCase());
    }

    public static String getRarity(String id) {
        return rarities.getOrDefault(id.toLowerCase(), "common");
    }

    public static Set<String> getTags(String id) {
        return tags.getOrDefault(id.toLowerCase(), Collections.emptySet());
    }

    public static boolean hasTag(String id, String tag) {
        return getTags(id).contains(tag.toUpperCase());
    }

    public static Set<String> getCategories() {
        return new HashSet<>(categories.values());
    }
}