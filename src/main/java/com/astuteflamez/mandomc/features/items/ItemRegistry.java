package com.astuteflamez.mandomc.features.items;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ItemRegistry {

    private static final Map<String, ItemStack> items = new HashMap<>();
    private static final Map<String, String> categories = new HashMap<>();

    public static void register(String id, ItemStack item, String category) {

        items.put(id, item);
        categories.put(id, category);
    }

    public static ItemStack get(String id) {

        ItemStack item = items.get(id);

        if (item == null) return null;

        return item.clone();
    }

    public static Set<String> getItemIds() {
        return items.keySet();
    }

    public static String getCategory(String id) {
        return categories.get(id);
    }

    public static Set<String> getCategories() {
        return new HashSet<>(categories.values());
    }
}