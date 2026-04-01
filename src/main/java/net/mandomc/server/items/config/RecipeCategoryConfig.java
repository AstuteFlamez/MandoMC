package net.mandomc.server.items.config;

import net.mandomc.MandoMC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/**
 * Loads recipe browser categories from data-folder YAML files.
 */
public final class RecipeCategoryConfig {

    public record RecipeCategoryDefinition(
            String id,
            String name,
            String icon,
            int slot,
            int guiSize,
            Map<String, Integer> itemSlots
    ) {
    }

    private static final Map<String, RecipeCategoryDefinition> categories = new HashMap<>();
    private static File recipesFolder;

    public static void setup() {
        MandoMC plugin = MandoMC.getInstance();

        recipesFolder = new File(plugin.getDataFolder(), "recipes");
        if (!recipesFolder.exists()) {
            recipesFolder.mkdirs();
        }

        copyDefaults(plugin);
        reload();
    }

    public static void reload() {
        categories.clear();

        if (recipesFolder == null || !recipesFolder.exists()) {
            return;
        }

        File[] files = recipesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            String name = config.getString("name", id);
            String icon = config.getString("icon", "BARRIER");
            int slot = config.getInt("slot", -1);
            int guiSize = config.getInt("gui-size", 54);
            Map<String, Integer> itemSlots = loadItemSlots(config);

            categories.put(id, new RecipeCategoryDefinition(
                    id,
                    name,
                    icon,
                    slot,
                    guiSize,
                    Map.copyOf(itemSlots)
            ));
        }
    }

    public static RecipeCategoryDefinition getCategory(String id) {
        return categories.get(id);
    }

    public static List<String> getCategoryIds() {
        List<RecipeCategoryDefinition> defs = new ArrayList<>(categories.values());
        defs.sort(Comparator.comparingInt(RecipeCategoryDefinition::slot).thenComparing(RecipeCategoryDefinition::id));

        List<String> ids = new ArrayList<>();
        for (RecipeCategoryDefinition def : defs) {
            ids.add(def.id());
        }
        return ids;
    }

    public static List<RecipeCategoryDefinition> getSortedCategories() {
        List<RecipeCategoryDefinition> defs = new ArrayList<>(categories.values());
        defs.sort(Comparator.comparingInt(RecipeCategoryDefinition::slot).thenComparing(RecipeCategoryDefinition::id));
        return defs;
    }

    private static Map<String, Integer> loadItemSlots(FileConfiguration config) {
        Map<String, Integer> itemSlots = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection("items");
        if (section != null) {
            for (String itemId : section.getKeys(false)) {
                itemSlots.put(itemId, section.getInt(itemId, -1));
            }
            return itemSlots;
        }

        // Backward compatibility for old list format.
        List<String> legacyItems = config.getStringList("items");
        int slot = 10;
        for (String itemId : legacyItems) {
            itemSlots.put(itemId, slot);
            slot++;
            if (slot % 9 == 0) {
                slot++;
            }
        }

        return itemSlots;
    }

    private static void copyDefaults(MandoMC plugin) {
        try {
            File jarFileLocation = new File(
                    MandoMC.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );

            try (JarFile jarFile = new JarFile(jarFileLocation)) {
                jarFile.stream().forEach(entry -> {
                    String name = entry.getName();
                    if (!name.startsWith("recipes/") || !name.endsWith(".yml")) {
                        return;
                    }

                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    File outputFile = new File(recipesFolder, fileName);
                    if (outputFile.exists()) {
                        return;
                    }

                    try (
                            InputStream input = jarFile.getInputStream(entry);
                            OutputStream output = new FileOutputStream(outputFile)
                    ) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = input.read(buffer)) > 0) {
                            output.write(buffer, 0, length);
                        }
                        plugin.getLogger().info("Generated recipe config: " + fileName);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to copy recipe config: " + fileName);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load recipe configs from jar.");
            e.printStackTrace();
        }
    }

    private RecipeCategoryConfig() {
        throw new UnsupportedOperationException("Utility class");
    }
}
