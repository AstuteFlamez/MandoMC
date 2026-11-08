package net.mandomc.gameplay.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/** Validated, immutable legacy-style shaped recipe shared by crafting and the guide. */
public record RecipeDefinition(String id, String result, int amount, String category,
        List<String> shape, Map<Character, String> ingredients) {
    public RecipeDefinition {
        shape = List.copyOf(shape);
        ingredients = Map.copyOf(ingredients);
    }

    public static RecipeDefinition parse(String id, ConfigurationSection config) {
        String path = "recipes.yml: recipes." + id;
        if (!id.matches("[a-z0-9_]+") || config == null) throw new IllegalArgumentException(path + ": invalid recipe");
        for (String key : config.getKeys(false)) {
            if (!Set.of("result", "amount", "category", "shape", "ingredients").contains(key)) {
                throw new IllegalArgumentException(path + ": unsupported key " + key);
            }
        }
        String result = config.getString("result", "");
        String category = config.getString("category", "");
        int amount = config.getInt("amount", 1);
        if (!result.matches("[a-z0-9_]+") || !category.matches("[a-z0-9_]+")
                || (config.contains("amount") && !config.isInt("amount")) || amount < 1 || amount > 64) {
            throw new IllegalArgumentException(path + ": invalid result, category or amount");
        }
        var raw = config.getList("shape");
        if (raw == null || raw.stream().anyMatch(row -> !(row instanceof String))) {
            throw new IllegalArgumentException(path + ": shape must contain strings");
        }
        var shape = config.getStringList("shape");
        if (shape.isEmpty() || shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty()
                || row.length() > 3 || row.length() != shape.getFirst().length())) {
            throw new IllegalArgumentException(path + ": shape must fit a rectangular 3x3 grid");
        }
        var section = config.getConfigurationSection("ingredients");
        if (section == null) throw new IllegalArgumentException(path + ": ingredients missing");
        var ingredients = new LinkedHashMap<Character, String>();
        for (String symbol : section.getKeys(false)) {
            String value = section.getString(symbol, "");
            if (symbol.length() != 1 || symbol.equals(" ") || !value.matches("(?:minecraft:)?[a-z0-9_]+")) {
                throw new IllegalArgumentException(path + ": invalid ingredient " + symbol);
            }
            ingredients.put(symbol.charAt(0), value);
        }
        var used = new java.util.HashSet<Character>();
        for (String row : shape) for (char symbol : row.toCharArray()) if (symbol != ' ') used.add(symbol);
        if (used.isEmpty() || !used.equals(ingredients.keySet())) {
            throw new IllegalArgumentException(path + ": ingredient symbols must exactly match the shape");
        }
        return new RecipeDefinition(id, result, amount, category, shape, ingredients);
    }
}
