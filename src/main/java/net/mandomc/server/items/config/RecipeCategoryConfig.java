package net.mandomc.server.items.config;

import net.mandomc.MandoMC;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads ordered recipe browser entries from data-folder recipes.yml.
 */
public final class RecipeCategoryConfig {

    private static final String RECIPES_FILE_NAME = "recipes.yml";
    private static final String RECIPES_KEY = "recipes";
    private static File recipesFile;
    private static List<String> orderedRecipeIds = List.of();

    public static void setup() {
        MandoMC plugin = MandoMC.getInstance();

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        recipesFile = new File(plugin.getDataFolder(), RECIPES_FILE_NAME);

        if (!recipesFile.exists()) {
            plugin.saveResource(RECIPES_FILE_NAME, false);
        }
        reload();
    }

    public static void reload() {
        if (recipesFile == null || !recipesFile.exists()) {
            orderedRecipeIds = List.of();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        List<String> ids = new ArrayList<>();
        for (String raw : config.getStringList(RECIPES_KEY)) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim().toLowerCase();
            if (id.isEmpty()) {
                continue;
            }
            ids.add(id);
        }
        orderedRecipeIds = List.copyOf(ids);
    }

    public static List<String> getOrderedRecipeIds() {
        return orderedRecipeIds;
    }

    private RecipeCategoryConfig() {
        throw new UnsupportedOperationException("Utility class");
    }
}
