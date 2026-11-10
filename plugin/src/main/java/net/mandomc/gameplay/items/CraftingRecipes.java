package net.mandomc.gameplay.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns native crafting registration and the recipe data displayed by the player guide. */
public final class CraftingRecipes implements AutoCloseable {
    private final ItemCatalog items;
    private final List<NamespacedKey> keys = new ArrayList<>();
    private final List<RecipeDefinition> definitions;
    private final java.util.Set<String> hiddenResults;
    private final java.util.Set<String> hiddenRecipes;

    public CraftingRecipes(JavaPlugin plugin, ItemCatalog items, ConfigurationSection config) {
        this.items = items;
        hiddenResults = java.util.Set.copyOf(config.getStringList("guide.hidden-results"));
        hiddenRecipes = java.util.Set.copyOf(config.getStringList("guide.hidden-recipes"));
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("items/recipes.yml: unsupported schema-version");
        var section = Objects.requireNonNull(config.getConfigurationSection("recipes"), "items/recipes.yml: recipes missing");
        var pending = new ArrayList<org.bukkit.inventory.Recipe>();
        var parsed = new ArrayList<RecipeDefinition>();
        for (String id : section.getKeys(false)) {
            var definition = RecipeDefinition.parse(id, section.getConfigurationSection(id));
            var result = Objects.requireNonNull(items.create(definition.result()), "Unknown recipe result: " + definition.result());
            if (definition.amount() > result.getMaxStackSize()) throw new IllegalArgumentException("Recipe output exceeds stack limit: " + id);
            result.setAmount(definition.amount());
            var key = new NamespacedKey(plugin, "craft_" + id);
            var shaped = definition.shapeless() ? null : new ShapedRecipe(key, result);
            var shapeless = definition.shapeless() ? new org.bukkit.inventory.ShapelessRecipe(key, result) : null;
            if (shaped != null) shaped.shape(definition.shape().toArray(String[]::new));
            for (var entry : definition.ingredients().entrySet()) {
                var ingredient = ingredient(entry.getValue());
                RecipeChoice choice = entry.getValue().startsWith("minecraft:")
                        ? new RecipeChoice.MaterialChoice(ingredient.getType()) : new RecipeChoice.ExactChoice(ingredient);
                if (shaped != null) shaped.setIngredient(entry.getKey(), choice);
                else shapeless.addIngredient(choice);
            }
            pending.add(shaped != null ? shaped : shapeless);
            parsed.add(definition);
        }
        var smelting = config.getConfigurationSection("smelting");
        if (smelting != null) {
            for (String id : smelting.getKeys(false)) {
                var definition = RecipeDefinition.parseSmelting(id, smelting.getConfigurationSection(id));
                var result = Objects.requireNonNull(items.create(definition.result()), "Unknown smelting result: " + id);
                String input = definition.ingredients().get('I');
                var ingredient = ingredient(input);
                RecipeChoice choice = input.startsWith("minecraft:") ? new RecipeChoice.MaterialChoice(ingredient.getType())
                        : new RecipeChoice.ExactChoice(ingredient);
                var key = new NamespacedKey(plugin, "smelt_" + id);
                pending.add(definition.blasting()
                        ? new org.bukkit.inventory.BlastingRecipe(key, result, choice, definition.experience(), definition.cookTime())
                        : new org.bukkit.inventory.FurnaceRecipe(key, result, choice, definition.experience(), definition.cookTime()));
                parsed.add(definition);
            }
        }
        definitions = List.copyOf(parsed);
        try {
            for (var recipe : pending) {
                if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Duplicate recipe: " + ((org.bukkit.Keyed) recipe).getKey());
                keys.add(((org.bukkit.Keyed) recipe).getKey());
            }
        } catch (RuntimeException error) {
            close();
            throw error;
        }
    }

    public List<RecipeDefinition> definitions() { return definitions; }

    public List<RecipeDefinition> guideDefinitions() {
        return definitions.stream().filter(recipe -> !hiddenResults.contains(recipe.result())
                && !hiddenRecipes.contains(recipe.id())).toList();
    }

    public ItemStack ingredient(String id) {
        if (!id.startsWith("minecraft:")) return Objects.requireNonNull(items.create(id), "Unknown recipe ingredient: " + id);
        Material material = Material.matchMaterial(id);
        if (material == null || material.isAir() || !material.isItem()) throw new IllegalArgumentException("Invalid recipe material: " + id);
        return new ItemStack(material);
    }

    public ItemStack result(RecipeDefinition recipe) {
        var result = Objects.requireNonNull(items.create(recipe.result()), "Missing recipe result: " + recipe.result());
        result.setAmount(recipe.amount());
        return result;
    }

    @Override public void close() {
        keys.forEach(Bukkit::removeRecipe);
        keys.clear();
    }
}
