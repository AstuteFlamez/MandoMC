package net.mandomc.gameplay.items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionRecipesTest {
    private YamlConfiguration recipes() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/items/recipes.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    @Test public void plastoidUsesFourSlotsAndProducesTwo() throws Exception {
        var recipe = RecipeDefinition.parse("plastoid", recipes().getConfigurationSection("recipes.plastoid"));
        assertTrue(recipe.shapeless());
        assertEquals("plastoid", recipe.result());
        assertEquals(2, recipe.amount());
        assertEquals(4, recipe.ingredients().size());
        assertEquals(2, recipe.ingredients().values().stream().filter("minecraft:clay_ball"::equals).count());
        assertTrue(recipe.ingredients().values().containsAll(List.of("minecraft:sand", "minecraft:coal")));
        assertEquals(List.of("ABC", "D  "), recipe.shape());
    }

    @Test public void rejectsAmbiguousOrInvalidShapelessDefinitions() throws Exception {
        for (Object value : List.of(List.of(), List.of("minecraft:clay_ball", 2),
                java.util.Collections.nCopies(10, "minecraft:coal"), List.of("minecraft:bad ingredient"))) {
            var config = recipes().getConfigurationSection("recipes.plastoid");
            config.set("ingredients", value);
            assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("plastoid", config));
        }
        var config = recipes().getConfigurationSection("recipes.plastoid");
        config.set("shape", List.of("ABC"));
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("plastoid", config));
        config.set("shape", null);
        config.set("type", "unknown");
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinition.parse("plastoid", config));
    }

    @Test public void onlyDiamondOreFamiliesAreReplaced() {
        for (Material material : Material.values()) {
            assertEquals(material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE,
                    OreDrops.durasteelOre(material));
        }
    }

    @Test public void approvedComponentQuantitiesAndCosts() throws Exception {
        var config = recipes();
        var expected = java.util.Map.ofEntries(
                java.util.Map.entry("straight_hilt", "durasteel=2,plastoid=1;out=1"),
                java.util.Map.entry("great_hilt", "durasteel=4,straight_hilt=1;out=1"),
                java.util.Map.entry("whip_hilt", "durasteel=1,electrum=1,straight_hilt=1;out=1"),
                java.util.Map.entry("pike_hilt", "durasteel=2,straight_hilt=1;out=1"),
                java.util.Map.entry("fang_hilt", "agrinium=2,straight_hilt=1;out=1"),
                java.util.Map.entry("barrel_liner", "agrinium=6;out=1"),
                java.util.Map.entry("alloy_plating", "agrinium=3;out=1"),
                java.util.Map.entry("wiring_coil", "electrum=4,plastoid=1;out=2"),
                java.util.Map.entry("energy_conduits", "electrum=2,plastoid=1;out=2"),
                java.util.Map.entry("fusion_coil_stabilizer", "durasteel=4,wiring_coil=1;out=1"),
                java.util.Map.entry("plasma_capacitor_array", "electrum=3,wiring_coil=2;out=1"),
                java.util.Map.entry("electrum_sheet", "electrum=3;out=1"),
                java.util.Map.entry("transparisteel_shard", "agrinium=4,minecraft:glass=1;out=4"),
                java.util.Map.entry("circuit_board", "electrum=1,plastoid=1,wiring_coil=2;out=1"),
                java.util.Map.entry("precision_crafted_lens", "electrum=4,transparisteel_shard=1;out=1"));
        for (var entry : expected.entrySet()) {
            var recipe = RecipeDefinition.parse(entry.getKey(), config.getConfigurationSection("recipes." + entry.getKey()));
            var counts = new java.util.TreeMap<String, Integer>();
            for (String row : recipe.shape()) for (char symbol : row.toCharArray()) if (symbol != ' ') {
                counts.merge(recipe.ingredients().get(symbol), 1, Integer::sum);
            }
            assertEquals(entry.getKey(), entry.getValue(), counts.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(","))
                    + ";out=" + recipe.amount());
        }
        assertFalse(config.contains("smelting.electrum_sheet"));
        var tie = RecipeDefinition.parse("tiefighter", config.getConfigurationSection("recipes.tiefighter"));
        assertEquals(List.of("WCW"), tie.shape());
        assertEquals(1, tie.guideRowOffset());
    }

    @Test public void everyVehicleDependencyHasASurvivalRoute() throws Exception {
        var config = recipes();
        var byResult = new java.util.HashMap<String, java.util.List<RecipeDefinition>>();
        for (String kind : List.of("recipes", "smelting")) {
            var section = config.getConfigurationSection(kind);
            for (String id : section.getKeys(false)) {
                var recipe = kind.equals("recipes") ? RecipeDefinition.parse(id, section.getConfigurationSection(id))
                        : RecipeDefinition.parseSmelting(id, section.getConfigurationSection(id));
                byResult.computeIfAbsent(recipe.result(), ignored -> new java.util.ArrayList<>()).add(recipe);
            }
        }
        for (String id : List.of("speederbike", "uwing", "xwing", "tiefighter")) {
            assertTrue(id + " must be obtainable without event loot", reachable(id, byResult, new java.util.HashSet<>()));
        }
    }

    private boolean reachable(String id, java.util.Map<String, java.util.List<RecipeDefinition>> recipes,
                              java.util.Set<String> path) {
        if (id.startsWith("minecraft:") || java.util.Set.of("durasteel_scrap", "electrum_scrap", "agrinium_scrap", "beskar_scrap").contains(id)) return true;
        if (!path.add(id)) return false;
        for (var recipe : recipes.getOrDefault(id, List.of())) {
            if (recipe.ingredients().values().stream().allMatch(input -> reachable(input, recipes, new java.util.HashSet<>(path)))) {
                return true;
            }
        }
        return false;
    }
}
