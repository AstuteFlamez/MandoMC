package net.mandomc.system.vehicles.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.mandomc.MandoMC;

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
}