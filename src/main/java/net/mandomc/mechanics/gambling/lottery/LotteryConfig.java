package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.mandomc.MandoMC;

import java.io.File;

/**
 * Handles loading and access of lottery configuration.
 */
public class LotteryConfig {

    private static File file;
    private static FileConfiguration config;

    /**
     * Loads the lottery configuration file from disk.
     */
    public static void load() {

        File folder = new File(MandoMC.getInstance().getDataFolder(), "gambling");
        if (!folder.exists()) folder.mkdirs();

        file = new File(folder, "lottery.yml");

        if (!file.exists()) {
            MandoMC.getInstance().saveResource("gambling/lottery.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Gets the loaded configuration.
     *
     * @return file configuration instance
     */
    public static FileConfiguration get() {
        return config;
    }
}