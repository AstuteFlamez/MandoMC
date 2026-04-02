package net.mandomc.server.shop.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data class representing a fully loaded shop configuration.
 */
public class Shop {

    public static final int INVENTORY_SIZE = 54;

    private final String id;
    private final List<ShopItem> items;

    public Shop(String id, List<ShopItem> items) {
        this.id = id;
        this.items = Collections.unmodifiableList(items);
    }

    public String getId() { return id; }
    public int getSize() { return INVENTORY_SIZE; }

    /** Ordered shop items in config order. */
    public List<ShopItem> getItems() { return items; }
}
