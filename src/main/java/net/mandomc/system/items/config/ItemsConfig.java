package net.mandomc.system.items.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.mandomc.MandoMC;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * Manages all item configuration files.
 *
 * Loads all YAML files from the items folder, copies defaults
 * from the plugin jar, and provides access to item data.
 */
public class ItemsConfig {

    private static final Map<String, FileConfiguration> configs = new HashMap<>();
    private static final Map<String, File> files = new HashMap<>();

    private static File itemsFolder;

    /**
     * Initializes the items configuration system.
     *
     * Creates the items folder, copies default configs,
     * and loads all configuration files.
     */
    public static void setup() {

        MandoMC plugin = MandoMC.getInstance();

        itemsFolder = new File(plugin.getDataFolder(), "items");

        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
        }

        copyDefaults(plugin);
        loadAll();
    }

    /**
     * Copies all default item config files from the plugin jar.
     */
    private static void copyDefaults(MandoMC plugin) {

        try {

            File jarFileLocation = new File(
                    MandoMC.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );

            try (JarFile jarFile = new JarFile(jarFileLocation)) {

                jarFile.stream().forEach(entry -> {

                    String name = entry.getName();

                    if (!name.startsWith("items/") || !name.endsWith(".yml")) return;

                    String fileName = name.substring(name.lastIndexOf("/") + 1);
                    File outputFile = new File(itemsFolder, fileName);

                    if (outputFile.exists()) return;

                    try (
                            InputStream input = jarFile.getInputStream(entry);
                            OutputStream output = new FileOutputStream(outputFile)
                    ) {

                        byte[] buffer = new byte[1024];
                        int length;

                        while ((length = input.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }

                        plugin.getLogger().info("Generated item config: " + fileName);

                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to copy item config: " + fileName);
                        e.printStackTrace();
                    }
                });
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load item configs from jar.");
            e.printStackTrace();
        }
    }

    /**
     * Loads all YAML config files from the items folder.
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

        MandoMC.getInstance().getLogger()
                .info("Loaded " + configs.size() + " item config files.");
    }

    /**
     * Gets a specific config by id (filename without extension).
     *
     * @param id config id
     * @return configuration or null
     */
    public static FileConfiguration get(String id) {
        return configs.get(id);
    }

    /**
     * Gets all loaded item configs.
     *
     * @return map of config id to configuration
     */
    public static Map<String, FileConfiguration> getAll() {
        return configs;
    }

    /**
     * Saves a specific config file.
     *
     * @param id config id
     */
    public static void save(String id) {

        FileConfiguration config = configs.get(id);
        File file = files.get(id);

        if (config == null || file == null) return;

        try {
            config.save(file);
        } catch (IOException e) {
            MandoMC.getInstance().getLogger()
                    .severe("Couldn't save item config: " + id);
            e.printStackTrace();
        }
    }

    /**
     * Saves all loaded configs.
     */
    public static void saveAll() {
        for (String id : configs.keySet()) {
            save(id);
        }
    }

    /**
     * Reloads all item configs.
     */
    public static void reload() {
        loadAll();
    }

    /**
     * Retrieves a specific item's configuration section.
     *
     * Searches all loaded config files for the given item id.
     *
     * @param itemId the item id
     * @return the item section or null if not found
     */
    public static ConfigurationSection getItemSection(String itemId) {

        for (FileConfiguration config : configs.values()) {

            ConfigurationSection items = config.getConfigurationSection("items");
            if (items == null) continue;

            ConfigurationSection section = items.getConfigurationSection(itemId);
            if (section != null) return section;
        }

        return null;
    }
}