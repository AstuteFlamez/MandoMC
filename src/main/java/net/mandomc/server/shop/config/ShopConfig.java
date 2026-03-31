package net.mandomc.server.shop.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads and provides typed access to all shop definition files.
 *
 * Each {@code *.yml} inside the {@code shops/} data folder defines one shop.
 * The shop id is the file's base name (without extension).
 */
public class ShopConfig {

    private final Plugin plugin;
    private final Logger logger;
    private final File shopsFolder;

    /** shop-id → loaded FileConfiguration */
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ShopConfig(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.shopsFolder = new File(plugin.getDataFolder(), "shops");
    }

    /**
     * Loads (or reloads) all shop configs from the data folder.
     */
    public void reload() {
        configs.clear();

        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
            return;
        }

        File[] files = shopsFolder.listFiles(f -> f.getName().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            configs.put(id, YamlConfiguration.loadConfiguration(file));
        }

        logger.info("Loaded " + configs.size() + " shop(s).");
    }

    /**
     * Returns shop identifiers currently loaded (file base names).
     */
    public Set<String> getShopIds() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    /**
     * Returns the full {@link FileConfiguration} for a shop.
     *
     * @param shopId shop file base name without {@code .yml}
     */
    public FileConfiguration getShopConfig(String shopId) {
        return configs.get(shopId);
    }

    /**
     * Returns the {@code permission} value for a shop, or an empty string
     * if none is configured (meaning no permission is required).
     *
     * @param shopId shop file base name
     */
    public String getPermission(String shopId) {
        FileConfiguration fc = configs.get(shopId);
        if (fc == null) return "";
        String perm = fc.getString("permission");
        return perm != null ? perm : "";
    }

    /**
     * Returns the GUI configuration section for a shop.
     *
     * @param shopId shop file base name
     */
    public ConfigurationSection getGuiSection(String shopId) {
        FileConfiguration fc = configs.get(shopId);
        return fc != null ? fc.getConfigurationSection("gui") : null;
    }

    /**
     * Returns the items section for a shop.
     *
     * @param shopId shop file base name
     */
    public ConfigurationSection getItemsSection(String shopId) {
        FileConfiguration fc = configs.get(shopId);
        return fc != null ? fc.getConfigurationSection("items") : null;
    }
}
