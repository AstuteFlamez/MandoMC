package net.mandomc.gameplay.vehicle.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.mandomc.MandoMC;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class VehicleConfig {

    private static final Map<String, FileConfiguration> configs = new HashMap<>();
    private static File vehiclesFolder;

    public static void setup() {

        MandoMC plugin = MandoMC.getInstance();

        vehiclesFolder = new File(plugin.getDataFolder(), "vehicles");

        if (!vehiclesFolder.exists()) {
            vehiclesFolder.mkdirs();
        }

        loadAll();
    }

    public static void loadAll() {

        configs.clear();

        File[] list = vehiclesFolder.listFiles();
        if (list == null) return;

        for (File file : list) {

            if (!file.getName().endsWith(".yml")) continue;

            String id = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            configs.put(id, config);
            validateVehicleConfig(id, config);
        }

        MandoMC.getInstance().getLogger()
                .info("Loaded " + configs.size() + " vehicle configs.");
    }

    public static FileConfiguration get(String id) {
        return configs.get(id);
    }

    public static Map<String, FileConfiguration> getAll() {
        return configs;
    }

    public static void reload() {
        loadAll();
    }

    private static void validateVehicleConfig(String vehicleId, FileConfiguration config) {
        validateSection(vehicleId, config, "vehicle");
        validateSection(vehicleId, config, "vehicle.stats");
        validateSection(vehicleId, config, "vehicle.systems");
        validateSection(vehicleId, config, "vehicle.seats");
        validateSection(vehicleId, config, "vehicle.skins");
        validateSection(vehicleId, config, "vehicle.skins.options");
        validateKey(vehicleId, config, "vehicle.display_name");
        validateKey(vehicleId, config, "vehicle.skins.default");
    }

    private static void validateSection(String vehicleId, FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null && !config.isList(path)) {
            MandoMC.getInstance().getLogger().warning("[Vehicles] " + vehicleId + ".yml missing section: " + path);
        }
    }

    private static void validateKey(String vehicleId, FileConfiguration config, String path) {
        if (!config.contains(path)) {
            MandoMC.getInstance().getLogger().warning("[Vehicles] " + vehicleId + ".yml missing key: " + path);
        }
    }
}