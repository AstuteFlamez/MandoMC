package net.mandomc.gameplay.vehicle.config;

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
 * Loads and provides typed access to all vehicle definition files.
 *
 * Each {@code *.yml} file inside the {@code vehicles/} data folder represents
 * one vehicle definition, keyed by the file's base name (without extension).
 */
public class VehicleDefinitionConfig {

    private final Plugin plugin;
    private final Logger logger;
    private final File vehiclesFolder;

    /** vehicle-id → loaded FileConfiguration */
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public VehicleDefinitionConfig(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.vehiclesFolder = new File(plugin.getDataFolder(), "vehicles");
    }

    /**
     * Loads (or reloads) all vehicle configs from the data folder.
     *
     * Creates the folder if it does not exist.
     */
    public void reload() {
        configs.clear();

        if (!vehiclesFolder.exists()) {
            vehiclesFolder.mkdirs();
            return;
        }

        File[] files = vehiclesFolder.listFiles(f -> f.getName().endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            configs.put(id, YamlConfiguration.loadConfiguration(file));
        }

        logger.info("Loaded " + configs.size() + " vehicle definition(s).");
    }

    /**
     * Returns the set of vehicle identifiers currently loaded.
     */
    public Set<String> getVehicleIds() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    /**
     * Returns the root configuration section for the given vehicle,
     * or {@code null} if unknown.
     *
     * @param vehicleId the vehicle file base name
     */
    public FileConfiguration getVehicleConfig(String vehicleId) {
        return configs.get(vehicleId);
    }

    /**
     * Returns a named section within a vehicle config.
     *
     * @param vehicleId the vehicle file base name
     * @param key       config key within the vehicle file
     */
    public ConfigurationSection getSection(String vehicleId, String key) {
        FileConfiguration fc = configs.get(vehicleId);
        return fc != null ? fc.getConfigurationSection(key) : null;
    }
}
