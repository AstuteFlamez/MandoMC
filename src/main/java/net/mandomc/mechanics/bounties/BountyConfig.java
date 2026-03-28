package net.mandomc.mechanics.bounties;

import net.mandomc.MandoMC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Handles loading and accessing bounty configuration.
 */
public class BountyConfig {

    private static File file;
    private static FileConfiguration config;

    /**
     * Initializes the bounty config file.
     *
     * @param plugin plugin instance
     */
    public static void setup(MandoMC plugin) {

        File dir = new File(plugin.getDataFolder(), "bounties");
        if (!dir.exists()) dir.mkdirs();

        file = new File(dir, "bounty.yml");

        if (!file.exists()) {
            plugin.saveResource("bounties/bounty.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Reloads the config from disk.
     */
    public static void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Gets the config instance.
     *
     * @return config
     */
    public static FileConfiguration get() {
        return config;
    }

    /**
     * Gets min bounty.
     */
    public static double getMinBounty() {
        return config.getDouble("bounty.min", 100);
    }

    /**
     * Gets max bounty.
     */
    public static double getMaxBounty() {
        return config.getDouble("bounty.max", 100000);
    }

    /**
     * Gets tracking interval in seconds.
     */
    public static int getTrackingInterval() {
        return config.getInt("bounty.tracking-interval", 600);
    }
}