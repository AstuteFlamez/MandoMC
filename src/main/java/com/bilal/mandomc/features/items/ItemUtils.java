package com.bilal.mandomc.features.items;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemUtils {

    public static String getItemId(ItemStack item) {

        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer()
                .get(ItemKeys.ITEM_ID, PersistentDataType.STRING);
    }

    public static boolean isCustomItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public static boolean hasTag(ItemStack item, String tag) {

        for (String t : getTags(item)) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getTags(ItemStack item) {

        List<String> result = new ArrayList<>();

        if (item == null) return result;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return result;

        String tags = meta.getPersistentDataContainer()
                .get(ItemKeys.ITEM_TAGS, PersistentDataType.STRING);

        if (tags == null || tags.isEmpty()) return result;

        for (String tag : tags.split(",")) {
            result.add(tag.trim());
        }

        return result;
    }

    public static boolean isItem(ItemStack item, String id) {

        String itemId = getItemId(item);

        if (itemId == null) return false;

        return itemId.equals(id);
    }
}