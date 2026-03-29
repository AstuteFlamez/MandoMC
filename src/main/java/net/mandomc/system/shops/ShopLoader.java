package net.mandomc.system.shops;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ShopLoader {

    public static void loadAll(File folder) {

        ShopManager.clear();

        if (!folder.exists()) {
            folder.mkdirs();
            Bukkit.getLogger().info("[Shops] Created shops folder.");
        }

        File[] files = folder.listFiles();

        if (files == null || files.length == 0) {
            Bukkit.getLogger().warning("[Shops] No shop files found.");
            return;
        }

        for (File file : files) {

            if (!file.getName().endsWith(".yml")) continue;

            try {

                String id = file.getName().replace(".yml", "").toLowerCase();
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

                // ✅ TITLE (safe)
                String title = config.getString("gui.title");
                if (title == null) {
                    title = "&cShop";
                    Bukkit.getLogger().warning("[Shops] Missing gui.title in " + file.getName());
                }

                // ✅ SIZE (safe + validated)
                int size = config.getInt("gui.size");

                if (size <= 0) {
                    Bukkit.getLogger().severe("[Shops] Shop '" + id + "' has size = 0. Check gui.size in " + file.getName());
                    size = 54;
                }

                if (size % 9 != 0 || size > 54) {
                    Bukkit.getLogger().warning("[Shops] Invalid size (" + size + ") in " + file.getName() + ", forcing 54");
                    size = 54;
                }

                // ✅ FILLER (FIXED — YOU WERE MISSING THIS)
                ShopItem filler = null;

                ConfigurationSection fillerSection = config.getConfigurationSection("gui.filler");
                if (fillerSection != null) {

                    Material mat = Material.matchMaterial(fillerSection.getString("material", "BLACK_STAINED_GLASS_PANE"));
                    if (mat == null) mat = Material.BLACK_STAINED_GLASS_PANE;

                    ItemStack display = ShopUtils.buildDisplayItem(fillerSection);

                    filler = new ShopItem(
                            ShopItem.Type.GENERIC, // filler type (safe fallback)
                            "filler",
                            1,
                            0,
                            display
                    );
                }

                // ✅ ITEMS
                Map<Integer, ShopItem> items = new HashMap<>();

                ConfigurationSection section = config.getConfigurationSection("items");

                if (section != null) {
                    for (String key : section.getKeys(false)) {

                        ConfigurationSection s = section.getConfigurationSection(key);
                        if (s == null) continue;

                        int slot = s.getInt("slot", -1);
                        if (slot < 0) continue;

                        String typeStr = s.getString("type");
                        if (typeStr == null) {
                            Bukkit.getLogger().warning("[Shops] Missing type in " + file.getName() + " -> " + key);
                            continue;
                        }

                        ShopItem.Type type;
                        try {
                            type = ShopItem.Type.valueOf(typeStr.toUpperCase());
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[Shops] Invalid type '" + typeStr + "' in " + file.getName());
                            continue;
                        }

                        String itemId = s.getString("id");
                        int amount = s.getInt("amount", 1);
                        int price = s.getInt("price.amount", 0);

                        ItemStack display = ShopUtils.buildDisplayItem(s);

                        items.put(slot, new ShopItem(type, itemId, amount, price, display));
                    }
                }

                // ✅ MESSAGES
                ConfigurationSection msg = config.getConfigurationSection("messages");

                ShopMessages messages = new ShopMessages(
                        msg != null ? msg.getString("prefix", "") : "",
                        msg != null ? msg.getString("not-enough-money", "") : "",
                        msg != null ? msg.getString("purchased", "") : "",
                        msg != null ? msg.getString("inventory-full", "") : ""
                );

                // ✅ FINAL SHOP
                Shop shop = new Shop(id, title, size, filler, items, messages);

                ShopManager.register(id, shop);

                Bukkit.getLogger().info("[Shops] Loaded shop: " + id);

            } catch (Exception e) {
                Bukkit.getLogger().severe("[Shops] Failed to load: " + file.getName());
                e.printStackTrace();
            }
        }

        Bukkit.getLogger().info("[Shops] Total loaded: " + ShopManager.getAll().size());
    }
}