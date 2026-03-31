package net.mandomc.server.shop.model;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable data class representing a fully loaded shop configuration.
 */
public class Shop {

    private final String id;
    private final String title;
    private final int size;
    private final ItemStack filler;
    private final Map<Integer, ShopItem> items;

    public Shop(String id, String title, int size, ItemStack filler, Map<Integer, ShopItem> items) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.filler = filler;
        this.items = Collections.unmodifiableMap(items);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getSize() { return size; }

    /** The filler item displayed in empty GUI slots, or null if no filler is configured. */
    public ItemStack getFiller() { return filler; }

    /** Map of inventory slot → ShopItem. */
    public Map<Integer, ShopItem> getItems() { return items; }
}
