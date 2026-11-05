package net.mandomc.world.ilum.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.List;

/**
 * Typed configuration for the Ilum parkour system.
 *
 * Wraps {@code parkour.yml}.
 */
public class ParkourConfig extends BaseConfig {

    private static ParkourConfig INSTANCE;

    public ParkourConfig(Plugin plugin) {
        super(plugin, "parkour.yml");
        INSTANCE = this;
        validate();
    }

    /**
     * Backward-compatible static accessor.
     * Returns the raw {@link FileConfiguration} for callers that have not yet
     * been migrated to the typed API.
     */
    public static FileConfiguration get() {
        if (INSTANCE == null) {
            return new YamlConfiguration();
        }
        return INSTANCE.raw();
    }

    /** World name where the parkour course is located. */
    public String getWorld() {
        return getString("parkour.world", "ilum");
    }

    /** Raw start-location section ({@code x}, {@code y}, {@code z}, {@code yaw}, {@code pitch}). */
    public ConfigurationSection getStartSection() {
        return getSection("parkour.start");
    }

    /** Raw leaderboard configuration section. */
    public ConfigurationSection getLeaderboardsSection() {
        return getSection("parkour.leaderboards");
    }

    /** Raw spawn-location section used when a player finishes or fails. */
    public ConfigurationSection getSpawnSection() {
        return getSection("parkour.spawn");
    }

    /** Commands allowed during the parkour run (e.g. {@code msg}, {@code help}). */
    public List<String> getAllowedCommands() {
        return getStringList("parkour.settings.allow-commands");
    }

    /** Commands executed as console when a player completes the course. */
    public List<String> getRewardCommands() {
        return getStringList("parkour.rewards.commands");
    }

    /** Saves the in-memory config state to disk. */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("Failed to save parkour.yml: " + e.getMessage());
        }
    }

    @Override
    public void reload() {
        super.reload();
        validate();
    }

    private void validate() {
        warnMissingSection("parkour");
        warnMissingSection("parkour.start");
        warnMissingSection("parkour.spawn");
        warnMissingSection("parkour.checkpoints");
        warnMissingSection("parkour.rewards");
    }

    private void warnMissingSection(String path) {
        if (getSection(path) == null) {
            logger.warning("[parkour.yml] Missing section '" + path + "'.");
        }
    }
}
