package net.mandomc.system.shops;

import java.util.HashMap;
import java.util.Map;

public class ShopManager {

    private static final Map<String, Shop> SHOPS = new HashMap<>();

    public static void register(String id, Shop shop) {
        SHOPS.put(id.toLowerCase(), shop);
    }

    public static Shop get(String id) {
        if (id == null) return null;
        return SHOPS.get(id.toLowerCase());
    }

    public static Map<String, Shop> getAll() {
        return SHOPS;
    }

    public static void clear() {
        SHOPS.clear();
    }
}