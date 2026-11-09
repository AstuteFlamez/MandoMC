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

    public CraftingRecipes(JavaPlugin plugin, ItemCatalog items, ConfigurationSection config) {
        this.items = items;
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("items/recipes.yml: unsupported schema-version");
        var section = Objects.requireNonNull(config.getConfigurationSection("recipes"), "items/recipes.yml: recipes missing");
        var pending = new ArrayList<ShapedRecipe>();
        var parsed = new ArrayList<RecipeDefinition>();
        for (String id : section.getKeys(false)) {
            var definition = RecipeDefinition.parse(id, section.getConfigurationSection(id));
            var result = Objects.requireNonNull(items.create(definition.result()), "Unknown recipe result: " + definition.result());
            if (definition.amount() > result.getMaxStackSize()) throw new IllegalArgumentException("Recipe output exceeds stack limit: " + id);
            result.setAmount(definition.amount());
            var recipe = new ShapedRecipe(new NamespacedKey(plugin, "craft_" + id), result);
            recipe.shape(definition.shape().toArray(String[]::new));
            for (var entry : definition.ingredients().entrySet()) {
                var ingredient = ingredient(entry.getValue());
                RecipeChoice choice = entry.getValue().startsWith("minecraft:")
                        ? new RecipeChoice.MaterialChoice(ingredient.getType()) : new RecipeChoice.ExactChoice(ingredient);
                recipe.setIngredient(entry.getKey(), choice);
            }
            pending.add(recipe);
            parsed.add(definition);
        }
        definitions = List.copyOf(parsed);
        try {
            for (var recipe : pending) {
                if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Duplicate recipe: " + recipe.getKey());
                keys.add(recipe.getKey());
            }
        } catch (RuntimeException error) {
            close();
            throw error;
        }
    }

    public List<RecipeDefinition> definitions() { return definitions; }

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
