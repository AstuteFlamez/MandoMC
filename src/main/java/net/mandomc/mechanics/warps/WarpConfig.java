package net.mandomc.mechanics.warps;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.mandomc.MandoMC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles loading, saving, and reloading of the warps configuration file.
 *
 * Manages the warps.yml file located in the plugin data folder.
 */
public class WarpConfig {

    private static final String FILE_NAME = "warps.yml";

    private static File file;
    private static FileConfiguration config;

    /**
     * Initializes the warps configuration file.
     *
     * Creates the file if it does not exist by copying the default
     * resource from the plugin jar.
     */
    public static void setup() {

        MandoMC plugin = MandoMC.getInstance();

        file = new File(plugin.getDataFolder(), FILE_NAME);

        if (!file.exists()) {
            copyDefaultFile(plugin);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Gets the loaded configuration.
     *
     * @return the warps configuration
     */
    public static FileConfiguration get() {
        return config;
    }

    /**
     * Saves the configuration to disk.
     */
    public static void save() {
        if (config == null || file == null) return;

        try {
            config.save(file);
        } catch (IOException e) {
            MandoMC.getInstance().getLogger().severe("Failed to save warps.yml");
            e.printStackTrace();
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public static void reload() {
        if (file == null) return;
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Copies the default warps.yml from the plugin resources.
     */
    private static void copyDefaultFile(MandoMC plugin) {

        try (InputStream input = plugin.getResource(FILE_NAME);
             OutputStream output = new FileOutputStream(file)) {

            if (input == null) {
                plugin.getLogger().warning("Default warps.yml not found in resources.");
                return;
            }

            byte[] buffer = new byte[1024];
            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default warps.yml");
            e.printStackTrace();
        }
    }
}