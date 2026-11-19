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
                "koth", "jabba", "jabba-loot", "dioxis", "massiff", "dewback", "vehicle-ammo")) {
            var items = resource("items/definitions/" + file + ".yml").getConfigurationSection("items");
            for (String id : items.getKeys(false)) {
                assertTrue("Duplicate " + id, ids.add(id));
                ItemDefinition.parse(id, items.getConfigurationSection(id), file);
            }
        }
        for (String id : resource("items/catalog.yml").getConfigurationSection("external-items").getKeys(false)) {
            assertTrue("Duplicate external item " + id, ids.add(id));
        }
        var config = resource("items/recipes.yml");
        var shapes = new HashSet<String>();
        for (String section : java.util.List.of("recipes", "smelting", "stonecutting")) {
            var recipes = config.getConfigurationSection(section);
            for (String id : recipes.getKeys(false)) {
                var recipe = section.equals("recipes") ? RecipeDefinition.parse(id, recipes.getConfigurationSection(id))
                        : section.equals("smelting") ? RecipeDefinition.parseSmelting(id, recipes.getConfigurationSection(id))
                        : RecipeDefinition.parseStonecutting(id, recipes.getConfigurationSection(id));
                assertTrue("Missing result " + id, recipe.result().startsWith("minecraft:") ? Material.matchMaterial(recipe.result()) != null : ids.contains(recipe.result()));
                if (!recipe.smelting() && !recipe.stonecutting()) {
                    String normal = pattern(recipe, false), mirrored = pattern(recipe, true);
                    String canonical = normal.compareTo(mirrored) <= 0 ? normal : mirrored;
                    assertTrue("Ambiguous native recipe " + id, shapes.add(canonical));
                }
                for (String ingredient : recipe.ingredients().values()) {
                    assertTrue("Missing ingredient " + ingredient, ingredient.startsWith("#minecraft:") || (ingredient.startsWith("minecraft:")
                            ? Material.matchMaterial(ingredient) != null : ids.contains(ingredient)));
                }
            }
        }
        assertFalse(config.contains("recipes.bantha_hide_from_rabbit_hide"));
        assertTrue(config.getStringList("replace-native").contains("minecraft:leather"));
        assertEquals(125, config.getConfigurationSection("recipes").getKeys(false).size());
        assertEquals(12, config.getConfigurationSection("smelting").getKeys(false).size());
        assertEquals(70, config.getConfigurationSection("stonecutting").getKeys(false).size());
    }

    @Test public void kyberComesFromCratesAndPickaxeUsesIngots() throws Exception {
        var config = resource("items/recipes.yml");
        for (String id : config.getConfigurationSection("recipes").getKeys(false))
            assertFalse(config.getString("recipes." + id + ".result").endsWith("_kyber"));
        assertEquals("agrinium", config.getString("recipes.agrinium_pickaxe.ingredients.M"));
        assertEquals("blue_kyber", config.getString("recipes.obiwan.ingredients.C"));
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
            var config = resource("items/recipes.yml").getConfigurationSection("smelting.beskar_from_scrap");
            config.set("cook-time", value);
            assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_from_scrap", config));
        }
        var config = resource("items/recipes.yml").getConfigurationSection("smelting.beskar_from_scrap");
        config.set("furnace", "unknown");
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_from_scrap", config));
        config.set("furnace", "blast");
        config.set("experience", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parseSmelting("beskar_from_scrap", config));
    }
}
