package com.astuteflamez.mandomc.system.items.configs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.astuteflamez.mandomc.core.MandoMC;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

public class ItemsConfig {

    private static final Map<String, FileConfiguration> configs = new HashMap<>();
    private static final Map<String, File> files = new HashMap<>();

    private static File itemsFolder;

    /*
     * Setup items folder, copy defaults, and load configs
     */
    public static void setup() {

        itemsFolder = new File(MandoMC.getInstance().getDataFolder(), "items");

        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
        }

        copyDefaults();
        loadAll();
    }

    /*
     * Copies every .yml inside resources/items/ into the items folder
     */
    private static void copyDefaults() {

        try {

            File jar = new File(
                    MandoMC.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            try (JarFile jarFile = new JarFile(jar)) {

                jarFile.stream().forEach(entry -> {

                    String name = entry.getName();

                    if (!name.startsWith("items/") || !name.endsWith(".yml")) return;

                    String fileName = name.substring(name.lastIndexOf("/") + 1);

                    File outFile = new File(itemsFolder, fileName);

                    if (outFile.exists()) return;

                    try (
                            InputStream input = jarFile.getInputStream(entry);
                            OutputStream output = new FileOutputStream(outFile)
                    ) {

                        byte[] buffer = new byte[1024];
                        int length;

                        while ((length = input.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }

                        System.out.println("[MandoMC] Generated item config: " + fileName);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Loads every .yml file in the items folder
     */
    public static void loadAll() {

        configs.clear();
        files.clear();

        File[] list = itemsFolder.listFiles();

        if (list == null) return;

        for (File file : list) {

            if (!file.getName().endsWith(".yml")) continue;

            String id = file.getName().replace(".yml", "");

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            configs.put(id, config);
            files.put(id, file);
        }

        System.out.println("[MandoMC] Loaded " + configs.size() + " item config files.");
    }

    /*
     * Get config by filename
     * Example: ItemsConfig.get("lightsabers")
     */
    public static FileConfiguration get(String id) {
        return configs.get(id);
    }

    /*
     * Get all configs
     */
    public static Map<String, FileConfiguration> getAll() {
        return configs;
    }

    /*
     * Save a specific config
     */
    public static void save(String id) {

        FileConfiguration config = configs.get(id);
        File file = files.get(id);

        if (config == null || file == null) return;

        try {
            config.save(file);
        } catch (IOException e) {
            System.out.println("[MandoMC] Couldn't save item config: " + id);
        }
    }

    /*
     * Save all configs
     */
    public static void saveAll() {

        for (String id : configs.keySet()) {
            save(id);
        }
    }

    /*
     * Reload entire items folder
     */
    public static void reload() {
        loadAll();
    }

    public static ConfigurationSection getItemSection(String itemId) {

        for (FileConfiguration config : configs.values()) {

            if (!config.contains("items")) continue;

            ConfigurationSection items = config.getConfigurationSection("items");

            if (items == null) continue;

            if (items.contains(itemId)) {
                return items.getConfigurationSection(itemId);
            }
        }

        return null;
    }
}