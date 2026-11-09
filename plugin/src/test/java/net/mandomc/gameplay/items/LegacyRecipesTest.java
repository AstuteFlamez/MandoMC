package net.mandomc.gameplay.items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyRecipesTest {
    private YamlConfiguration resource(String path) throws Exception {
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            var config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    @Test public void allRecoveredRecipesResolveWithoutDuplicateItemIds() throws Exception {
        var ids = new HashSet<String>();
        for (String file : java.util.List.of("legacy", "vehicles", "robes", "materials", "sabers", "tusken",
                "jabba", "jabba-loot", "dioxis", "massiff", "dewback")) {
            var items = resource("items/definitions/" + file + ".yml").getConfigurationSection("items");
            for (String id : items.getKeys(false)) {
                assertTrue("Duplicate " + id, ids.add(id));
                ItemDefinition.parse(id, items.getConfigurationSection(id), file);
            }
        }
        var config = resource("items/recipes.yml");
        var shapes = new HashSet<String>();
        for (String section : java.util.List.of("recipes", "smelting")) {
            var recipes = config.getConfigurationSection(section);
            for (String id : recipes.getKeys(false)) {
                var recipe = section.equals("recipes") ? RecipeDefinition.parse(id, recipes.getConfigurationSection(id))
                        : RecipeDefinition.parseSmelting(id, recipes.getConfigurationSection(id));
                assertTrue("Missing result " + id, ids.contains(recipe.result()));
                if (!recipe.smelting()) {
                    String normal = pattern(recipe, false), mirrored = pattern(recipe, true);
                    String canonical = normal.compareTo(mirrored) <= 0 ? normal : mirrored;
                    assertTrue("Ambiguous native recipe " + id, shapes.add(canonical));
                }
                for (String ingredient : recipe.ingredients().values()) {
                    assertTrue("Missing ingredient " + ingredient, ingredient.startsWith("minecraft:")
                            ? Material.matchMaterial(ingredient) != null : ids.contains(ingredient));
                }
            }
        }
        assertEquals(51, config.getConfigurationSection("recipes").getKeys(false).size());
        assertEquals(11, config.getConfigurationSection("smelting").getKeys(false).size());
    }

    private String pattern(RecipeDefinition recipe, boolean mirror) {
        var rows = recipe.shape();
        int min = 3, max = -1;
        for (String row : rows) for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ') {
            min = Math.min(min, x); max = Math.max(max, x);
        }
        var result = new StringBuilder();
        for (String row : rows) {
            for (int x = min; x <= max; x++) result.append(recipe.ingredients().getOrDefault(row.charAt(mirror ? max + min - x : x), " ")).append('|');
            result.append('/');
        }
        return result.toString();
    }

    @Test public void onlyExplicitHelmetDefinitionsUseTheirHeadItemModel() throws Exception {
        var items = resource("items/definitions/legacy.yml").getConfigurationSection("items");
        for (String family : java.util.List.of("plastoid", "durasteel", "beskar")) {
            var config = items.getConfigurationSection(family + "_helmet");
            assertTrue(ItemDefinition.parse(family + "_helmet", config, "legacy").headModel());
            config.set("material", "PAPER");
            assertThrows(IllegalArgumentException.class, () -> ItemDefinition.parse("invalid", config, "legacy"));
            config.set("material", "IRON_HELMET");
            config.set("equipment-model", "mandomc:other");
            assertThrows(IllegalArgumentException.class, () -> ItemDefinition.parse("invalid", config, "legacy"));
        }
    }

    @Test public void blastRecipesRejectInvalidFurnacesTimingAndExperience() throws Exception {
        for (Object value : java.util.List.of(0, -1, 72001, "180", 2.5)) {
            var config = resource("items/recipes.yml").getConfigurationSection("smelting.beskar_sheet");
            config.set("cook-time", value);
            assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_sheet", config));
        }
        var config = resource("items/recipes.yml").getConfigurationSection("smelting.beskar_sheet");
        config.set("furnace", "unknown");
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_sheet", config));
        config.set("furnace", "blast");
        config.set("experience", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_sheet", config));
    }
}
