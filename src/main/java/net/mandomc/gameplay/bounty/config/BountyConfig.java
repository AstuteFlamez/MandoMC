package net.mandomc.gameplay.bounty.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Typed configuration for the bounty system.
 *
 * Wraps {@code bounties/bounty.yml}.
 */
public class BountyConfig extends BaseConfig {

    private static BountyConfig INSTANCE;

    public BountyConfig(Plugin plugin) {
        super(plugin, "bounties/bounty.yml");
        INSTANCE = this;
    }

    /** Backward-compatible static accessor (returns raw FileConfiguration). */
    public static FileConfiguration get() {
        return INSTANCE != null ? INSTANCE.raw() : null;
    }

    /** Minimum allowed bounty amount. */
    public double getMinAmount() {
        return getDouble("bounty.min", 100.0);
    }

    /** Maximum allowed bounty amount. */
    public double getMaxAmount() {
        return getDouble("bounty.max", 100000.0);
    }

    /** How often the bounty tracker updates target locations, in seconds. */
    public int getTrackingIntervalSeconds() {
        return getInt("bounty.tracking-interval", 600);
    }

    /** Raw showcase hologram configuration section. */
    public ConfigurationSection getShowcaseSection() {
        return getSection("bounty.showcase");
    }

    /** Raw GUI configuration section. */
    public ConfigurationSection getGuiSection() {
        return getSection("bounty.gui");
    }
}
