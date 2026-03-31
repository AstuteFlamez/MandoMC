package net.mandomc.server.shop;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.core.LangManager;
import net.mandomc.core.integration.OptionalPluginSupport;
import net.mandomc.server.items.ItemRegistry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.model.ShopItem;

/**
 * Loads all shop definitions from YAML files in the shops folder.
 *
 * Call {@link #loadAll(File)} to clear the registry and reload everything.
 * Each .yml file in the folder becomes one shop whose ID is the filename
 * without the extension.
 */
public final class ShopLoader {

    private static boolean warnedMissingWeaponMechanics;

    private ShopLoader() {}

    /**
     * Clears the shop registry and reloads all shops from the given folder.
     *
     * @param folder   the directory containing shop YAML files
     * @param pluginJar the plugin's jar file, used to copy default shop configs
     */
    public static void loadAll(File folder, File pluginJar) {

        ShopManager.clear();

        if (!folder.exists()) {
            folder.mkdirs();
        }

        copyDefaults(folder, pluginJar);

        File[] files = folder.listFiles();

        if (files == null || files.length == 0) {
            Bukkit.getLogger().warning("[Shops] No shop files found in: " + folder.getPath());
            return;
        }

        for (File file : files) {

            if (!file.getName().endsWith(".yml")) continue;

            try {
                loadShop(file);
            } catch (Exception e) {
                Bukkit.getLogger().severe("[Shops] Failed to load: " + file.getName());
                e.printStackTrace();
            }
        }

        Bukkit.getLogger().info("[Shops] Loaded " + ShopManager.getAll().size() + " shop(s).");
    }

    private static void loadShop(File file) {

        String id = file.getName().replace(".yml", "").toLowerCase();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // --- GUI settings ---
        String title = config.getString("gui.title", "&cShop");
        int size = config.getInt("gui.size", 54);

        if (size % 9 != 0 || size < 9 || size > 54) {
            Bukkit.getLogger().warning("[Shops] Invalid gui.size (" + size + ") in " + file.getName() + " — using 54.");
            size = 54;
        }

        // --- Filler ---
        ItemStack filler = null;
        ConfigurationSection fillerSection = config.getConfigurationSection("gui.filler");

        if (fillerSection != null) {
            Material mat = Material.matchMaterial(fillerSection.getString("material", "BLACK_STAINED_GLASS_PANE"));
            if (mat == null) mat = Material.BLACK_STAINED_GLASS_PANE;

            filler = new ItemStack(mat);
            ItemMeta meta = filler.getItemMeta();

            if (meta != null) {
                String name = fillerSection.getString("name", " ");
                meta.setDisplayName(color(name));
                filler.setItemMeta(meta);
            }
        }

        // --- Items ---
        Map<Integer, ShopItem> items = new HashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");

        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {

                ConfigurationSection s = itemsSection.getConfigurationSection(key);
                if (s == null) continue;

                int slot = s.getInt("slot", -1);

                if (slot < 0 || slot >= size) {
                    Bukkit.getLogger().warning("[Shops] Invalid slot for item '" + key + "' in " + file.getName() + " — skipping.");
                    continue;
                }

                String typeStr = s.getString("type");

                if (typeStr == null) {
                    Bukkit.getLogger().warning("[Shops] Missing type for item '" + key + "' in " + file.getName() + " — skipping.");
                    continue;
                }

                ShopItem.Type type;

                try {
                    type = ShopItem.Type.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[Shops] Unknown type '" + typeStr + "' for item '" + key + "' in " + file.getName() + " — skipping.");
                    continue;
                }

                String itemId = s.getString("id");

                if (itemId == null || itemId.isBlank()) {
                    Bukkit.getLogger().warning("[Shops] Missing id for item '" + key + "' in " + file.getName() + " — skipping.");
                    continue;
                }

                int amount = Math.max(1, s.getInt("amount", 1));
                int buyPrice = s.getInt("buy_price", 0);
                int sellPrice = s.getInt("sell_price", -1);

                items.put(slot, new ShopItem(type, itemId, amount, buyPrice, sellPrice));
            }
        }

        Shop shop = new Shop(id, title, size, filler, items);
        ShopManager.register(id, shop);

        Bukkit.getLogger().info("[Shops] Loaded shop: " + id + " (" + items.size() + " item(s))");
    }

    /**
     * Resolves the base ItemStack from the appropriate item system.
     */
    public static ItemStack resolveItem(ShopItem.Type type, String itemId, String fileName, String key) {
        return switch (type) {
            case WEAPON_MECHANICS_AMMO -> {
                if (!OptionalPluginSupport.hasWeaponMechanics()) {
                    warnMissingWeaponMechanics();
                    yield null;
                }
                ItemStack ammo = WeaponMechanicsAPI.generateAmmo(itemId, false);
                if (ammo == null) {
                    Bukkit.getLogger().warning("[Shops] WeaponMechanics ammo not found: '" + itemId + "' (" + key + " in " + fileName + ")");
                    yield null;
                }
                yield ammo.clone();
            }
            case WEAPON_MECHANICS_WEAPON -> {
                if (!OptionalPluginSupport.hasWeaponMechanics()) {
                    warnMissingWeaponMechanics();
                    yield null;
                }
                ItemStack weapon = WeaponMechanicsAPI.generateWeapon(itemId);
                if (weapon == null) {
                    Bukkit.getLogger().warning("[Shops] WeaponMechanics weapon not found: '" + itemId + "' (" + key + " in " + fileName + ")");
                    yield null;
                }
                yield weapon.clone();
            }
            case VANILLA -> {
                Material mat = Material.matchMaterial(itemId);
                if (mat == null) {
                    Bukkit.getLogger().warning("[Shops] Unknown vanilla material: '" + itemId + "' (" + key + " in " + fileName + ")");
                    yield null;
                }
                yield new ItemStack(mat);
            }
            case CUSTOM -> {
                ItemStack custom = ItemRegistry.get(itemId);
                if (custom == null) {
                    Bukkit.getLogger().warning("[Shops] Custom item not found in registry: '" + itemId + "' (" + key + " in " + fileName + ")");
                    yield null;
                }
                yield custom.clone();
            }
        };
    }

    /**
     * Copies default shop YAML files from the plugin jar to the shops folder
     * if they don't already exist.
     */
    private static void copyDefaults(File folder, File pluginJar) {

        if (pluginJar == null || !pluginJar.exists()) return;

        try (JarFile jar = new JarFile(pluginJar)) {

            jar.stream().forEach(entry -> {

                String name = entry.getName();

                if (!name.startsWith("shops/") || !name.endsWith(".yml")) return;

                String fileName = name.substring(name.lastIndexOf('/') + 1);
                File output = new File(folder, fileName);

                if (output.exists()) return;

                try (
                    InputStream in = jar.getInputStream(entry);
                    OutputStream out = new FileOutputStream(output)
                ) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    Bukkit.getLogger().info("[Shops] Created default config: " + fileName);
                } catch (IOException e) {
                    Bukkit.getLogger().severe("[Shops] Failed to copy default config: " + fileName);
                    e.printStackTrace();
                }
            });

        } catch (IOException e) {
            Bukkit.getLogger().severe("[Shops] Failed to read plugin jar for default shop configs.");
            e.printStackTrace();
        }
    }

    private static String color(String text) {
        return LangManager.colorize(text);
    }

    private static void warnMissingWeaponMechanics() {
        if (warnedMissingWeaponMechanics) {
            return;
        }
        warnedMissingWeaponMechanics = true;
        Bukkit.getLogger().warning("[Shops] WeaponMechanics integration unavailable. WeaponMechanics shop items are skipped.");
    }
}
