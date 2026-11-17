package net.mandomc.gameplay.items;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipeGuideOrderTest {
    private List<RecipeDefinition> definitions() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/items/recipes.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        var result = new ArrayList<RecipeDefinition>();
        var crafts = config.getConfigurationSection("recipes");
        for (String id : crafts.getKeys(false)) result.add(RecipeDefinition.parse(id, crafts.getConfigurationSection(id)));
        var styles = config.getConfigurationSection("stonecutting");
        for (String id : styles.getKeys(false)) result.add(RecipeDefinition.parseStonecutting(id, styles.getConfigurationSection(id)));
        return result;
    }

    @Test public void everySaberHasItsCrystalColorAndOwnCategory() throws Exception {
        var definitions = definitions();
        var order = new RecipeGuideOrder(definitions);
        var sabers = definitions.stream().filter(r -> r.category().equals("lightsabers")).toList();
        assertEquals(78, sabers.size());
        for (var saber : sabers) assertTrue(saber.id(), List.of("blue", "green", "red", "purple", "yellow", "orange", "white").contains(order.color(saber)));
        assertTrue(definitions.stream().anyMatch(r -> r.result().equals("e11") && r.category().equals("weapons")));
    }

    @Test public void variantsFollowTheirCraftedBaseWithinEachColor() throws Exception {
        var definitions = definitions();
        var order = new RecipeGuideOrder(definitions);
        var sorted = definitions.stream().filter(r -> r.category().equals("lightsabers")).sorted(order.comparator()).toList();
        var colors = sorted.stream().map(order::color).distinct().toList();
        assertEquals(List.of("blue", "green", "red", "purple", "yellow", "orange", "white"), colors);
        assertEquals("obiwan", sorted.getFirst().result());
        for (String color : colors) assertFalse(sorted.stream().filter(r -> order.color(r).equals(color)).findFirst().orElseThrow().stonecutting());
    }

    @Test public void missingOrCyclicStyleSourcesDoNotRecurseForever() {
        var config = new YamlConfiguration();
        config.set("input", "second"); config.set("result", "first"); config.set("category", "lightsabers");
        var first = RecipeDefinition.parseStonecutting("first", config);
        config.set("input", "first"); config.set("result", "second");
        var second = RecipeDefinition.parseStonecutting("second", config);
        assertEquals("", new RecipeGuideOrder(List.of(first, second)).color(first));
        assertEquals("", new RecipeGuideOrder(List.of(first)).color(first));
    }
}
