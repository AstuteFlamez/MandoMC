package net.mandomc.server.shop;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.mandomc.server.shop.model.Shop;

/**
 * Static registry for all loaded shops.
 *
 * Loaded by {@link ShopLoader} on startup/reload.
 * Queried by {@link ShopCommand} for opening GUIs and tab completion.
 */
public final class ShopManager {

    private static final Map<String, Shop> SHOPS = new HashMap<>();

    private ShopManager() {}

    public static void register(String id, Shop shop) {
        SHOPS.put(id.toLowerCase(), shop);
    }

    public static Shop get(String id) {
        if (id == null) return null;
        return SHOPS.get(id.toLowerCase());
    }

    public static Map<String, Shop> getAll() {
        return Collections.unmodifiableMap(SHOPS);
    }

    /** Returns all registered shop IDs for use in tab completion. */
    public static Set<String> getShopIds() {
        return SHOPS.keySet();
    }

    public static void clear() {
        SHOPS.clear();
    }
}
