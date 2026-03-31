package net.mandomc.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Abstract base for all typed configuration wrappers.
 *
 * Subclasses declare a specific YAML file path and expose typed getters.
 * Null-safe reads warn on missing keys rather than throwing.
 */
public abstract class BaseConfig {

    protected final Plugin plugin;
    protected final File file;
    protected final Logger logger;
    protected FileConfiguration config;

    /**
     * Creates and loads a config wrapping the given resource path.
     *
     * Copies the bundled default resource to the data folder if the file
     * does not yet exist.
     *
     * @param plugin       the owning plugin
     * @param resourcePath path relative to the plugin data folder (e.g. {@code "bounties/bounty.yml"})
     */
    protected BaseConfig(Plugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        this.logger = plugin.getLogger();

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Returns a string value, or {@code def} if the key is missing.
     * Logs a warning when the key is absent.
     */
    protected String getString(String key, String def) {
        String val = config.getString(key);
        if (val == null) {
            logger.warning(missingKey(key, String.valueOf(def)));
            return def;
        }
        return val;
    }

    /**
     * Returns an integer value, or {@code def} if the key is missing.
     * Logs a warning when the key is absent.
     */
    protected int getInt(String key, int def) {
        if (!config.contains(key)) {
            logger.warning(missingKey(key, String.valueOf(def)));
            return def;
        }
        return config.getInt(key, def);
    }

    /**
     * Returns a double value, or {@code def} if the key is missing.
     * Logs a warning when the key is absent.
     */
    protected double getDouble(String key, double def) {
        if (!config.contains(key)) {
            logger.warning(missingKey(key, String.valueOf(def)));
            return def;
        }
        return config.getDouble(key, def);
    }

    /**
     * Returns a boolean value, or {@code def} if the key is missing.
     * Logs a warning when the key is absent.
     */
    protected boolean getBoolean(String key, boolean def) {
        if (!config.contains(key)) {
            logger.warning(missingKey(key, String.valueOf(def)));
            return def;
        }
        return config.getBoolean(key, def);
    }

    /**
     * Returns a string list, or an empty list if the key is missing.
     */
    protected List<String> getStringList(String key) {
        return config.getStringList(key);
    }

    /**
     * Returns the section at the given key, or {@code null} if absent.
     */
    public ConfigurationSection getSection(String key) {
        return config.getConfigurationSection(key);
    }

    /**
     * Returns the raw {@link FileConfiguration} for sections not yet covered by typed getters.
     */
    public FileConfiguration raw() {
        return config;
    }

    private String missingKey(String key, String def) {
        return "[" + file.getName() + "] Missing config key '" + key + "', using default: " + def;
    }
}
