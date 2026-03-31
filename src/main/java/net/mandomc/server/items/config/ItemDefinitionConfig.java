package net.mandomc.server.items.config;

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
 * Loads and provides typed access to all item definition files.
 *
 * Each {@code *.yml} inside the {@code items/} data folder contains
 * one or more item definitions. Items are accessed by their string id
 * within a section, or by file name.
 */
public class ItemDefinitionConfig {

    private final Plugin plugin;
    private final Logger logger;
    private final File itemsFolder;

    /** filename (without .yml) → loaded FileConfiguration */
    private final Map<String, FileConfiguration> fileConfigs = new HashMap<>();

    public ItemDefinitionConfig(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.itemsFolder = new File(plugin.getDataFolder(), "items");
    }

    /**
     * Loads (or reloads) all item configs from the data folder.
     *
     * Creates the folder if it does not exist.
     */
    public void reload() {
        fileConfigs.clear();

        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
            return;
        }

        File[] files = itemsFolder.listFiles(f -> f.getName().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "");
            fileConfigs.put(name, YamlConfiguration.loadConfiguration(file));
        }

        logger.info("Loaded " + fileConfigs.size() + " item definition file(s).");
    }

    /**
     * Returns file base names currently loaded (e.g. {@code "sabers"}, {@code "weapons"}).
     */
    public Set<String> getFileNames() {
        return Collections.unmodifiableSet(fileConfigs.keySet());
    }

    /**
     * Returns the full {@link FileConfiguration} for an item file.
     *
     * @param fileName file base name without {@code .yml}
     */
    public FileConfiguration getFile(String fileName) {
        return fileConfigs.get(fileName);
    }

    /**
     * Returns a specific section within an item definition file.
     *
     * @param fileName file base name without {@code .yml}
     * @param key      section key within that file
     */
    public ConfigurationSection getSection(String fileName, String key) {
        FileConfiguration fc = fileConfigs.get(fileName);
        return fc != null ? fc.getConfigurationSection(key) : null;
    }

    /**
     * Searches all loaded files for a section keyed by {@code itemId}.
     *
     * Returns the first match found across all files, or {@code null} if none.
     *
     * @param itemId the item section identifier
     */
    public ConfigurationSection findItemSection(String itemId) {
        for (FileConfiguration fc : fileConfigs.values()) {
            if (fc.contains(itemId)) {
                return fc.getConfigurationSection(itemId);
            }
        }
        return null;
    }
}
