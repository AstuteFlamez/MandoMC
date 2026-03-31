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

    public WarpConfig(Plugin plugin) {
        super(plugin, "warps.yml");
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
