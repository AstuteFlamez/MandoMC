package net.mandomc.gameplay.items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipeDefinitionTest {
    private YamlConfiguration recipe() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
                result: robes_helmet
                amount: 1
                category: armor
                shape: ['BBB', 'B B']
                ingredients: {B: bantha_hide}
                """);
        return config;
    }

    @Test public void rejectsIncompleteOrUnusedIngredients() throws Exception {
        var missing = recipe();
        missing.set("ingredients.B", null);
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("hood", missing));
        var unused = recipe();
        unused.set("ingredients.X", "minecraft:stick");
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("hood", unused));
    }

    @Test public void rejectsMalformedShapesAndOutputCounts() throws Exception {
        for (var shape : List.of(List.of("BBBB"), List.of("BB", "B"), List.of("B", "B", "B", "B"), List.of(" "))) {
            var config = recipe();
            config.set("shape", shape);
            assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("hood", config));
        }
        for (Object amount : List.of(0, -1, 65, 1.5, "2")) {
            var config = recipe();
            config.set("amount", amount);
            assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("hood", config));
        }
    }

    @Test public void acceptsMixedCustomAndVanillaIngredientsWithoutMutableAliases() throws Exception {
        var config = recipe();
        config.set("shape", List.of("BS"));
        config.set("ingredients.S", "minecraft:stick");
        var result = RecipeDefinition.parse("hood", config);
        config.set("ingredients.B", "other_hide");
        assertEquals("bantha_hide", result.ingredients().get('B'));
        assertEquals("minecraft:stick", result.ingredients().get('S'));
        assertThrows(UnsupportedOperationException.class, () -> result.ingredients().put('B', "changed"));
        assertThrows(UnsupportedOperationException.class, () -> result.shape().add("B"));
    }

    @Test public void bundledRecipesResolveToExistingRobesAndRequireTwentyFourHides() throws Exception {
        var recipes = resource("recipes.yml").getConfigurationSection("recipes");
        var robes = resource("items/robes.yml").getConfigurationSection("items");
        var materials = resource("items/materials.yml").getConfigurationSection("items");
        int hides = 0;
        assertEquals(4, recipes.getKeys(false).size());
        for (String id : recipes.getKeys(false)) {
            var recipe = RecipeDefinition.parse(id, recipes.getConfigurationSection(id));
            assertNotNull(robes.getConfigurationSection(recipe.result()));
            assertEquals("bantha_hide", recipe.ingredients().get('B'));
            assertNotNull(materials.getConfigurationSection(recipe.ingredients().get('B')));
            hides += recipe.shape().stream().mapToInt(row -> (int) row.chars().filter(c -> c == 'B').count()).sum();
        }
        assertEquals(24, hides);
        var item = ItemDefinition.parse("bantha_hide", materials.getConfigurationSection("bantha_hide"), "materials.yml");
        assertEquals("mandomc:materials/bantha_hide", item.itemModel().toString());
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(path), StandardCharsets.UTF_8)) {
            var config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }
}
