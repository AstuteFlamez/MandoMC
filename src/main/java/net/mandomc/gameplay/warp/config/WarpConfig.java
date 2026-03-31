package net.mandomc.gameplay.warp.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Typed configuration for the warp system.
 *
 * Wraps {@code warps.yml}. Warp entries live under the {@code warps:} root key.
 */
public class WarpConfig extends BaseConfig {
    private static final String DEFAULT_GUI_TITLE = "&4&lMandoMC Warps";
    private static final int DEFAULT_GUI_SIZE = 54;

    public WarpConfig(Plugin plugin) {
        super(plugin, "warps.yml");
    }

    /**
     * Returns the configured GUI title for /warp menus.
     */
    public String getGuiTitle() {
        return getString("gui.title", DEFAULT_GUI_TITLE);
    }

    /**
     * Returns the configured GUI size normalized to Bukkit inventory rules.
     */
    public int getGuiSize() {
        int configured = getInt("gui.size", DEFAULT_GUI_SIZE);
        if (configured < 9 || configured > 54 || configured % 9 != 0) {
            logger.warning("[warps.yml] Invalid gui.size '" + configured + "', using default: " + DEFAULT_GUI_SIZE);
            return DEFAULT_GUI_SIZE;
        }
        return configured;
    }

    /**
     * Returns the optional filler section for the warp GUI.
     */
    public ConfigurationSection getGuiFillerSection() {
        return getSection("gui.filler");
    }

    /**
     * Returns the set of warp identifiers defined in the config.
     */
    public Set<String> getWarpIds() {
        ConfigurationSection section = getSection("warps");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    /**
     * Returns the configuration section for a specific warp.
     *
     * @param id the warp identifier as it appears in warps.yml
     * @return the section, or {@code null} if the warp does not exist
     */
    public ConfigurationSection getWarpSection(String id) {
        return getSection("warps." + id);
    }

    /**
     * Saves the current in-memory config to disk.
     */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("Failed to save warps.yml: " + e.getMessage());
        }
    }
}
