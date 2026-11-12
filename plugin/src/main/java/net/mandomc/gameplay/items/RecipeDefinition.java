package net.mandomc.gameplay.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/** Validated recipe shared by native registration and the guide; shapeless lists retain duplicate units. */
public record RecipeDefinition(String id, String result, int amount, String category,
        List<String> shape, Map<Character, String> ingredients, int cookTime, float experience, boolean shapeless, int guideRowOffset, boolean blasting) {
    public RecipeDefinition {
        shape = List.copyOf(shape);
        ingredients = Map.copyOf(ingredients);
    }

    public boolean smelting() { return cookTime > 0; }
    public boolean stonecutting() { return cookTime == -1; }

    public static RecipeDefinition parseStonecutting(String id, ConfigurationSection config) {
        String path = "items/recipes.yml: stonecutting." + id;
        if (!id.matches("[a-z0-9_]+") || config == null) throw new IllegalArgumentException(path + ": invalid recipe");
        if (!Set.of("result", "input", "category").containsAll(config.getKeys(false)))
            throw new IllegalArgumentException(path + ": unsupported key");
        String result = config.getString("result", "");
        String input = config.getString("input", "");
        String category = config.getString("category", "");
        if (!result.matches("(?:minecraft:)?[a-z0-9_]+") || !input.matches("[a-z0-9_]+") || input.equals(result)
                || !category.matches("[a-z0-9_]+")) throw new IllegalArgumentException(path + ": invalid result/input/category");
        return new RecipeDefinition(id, result, 1, category, List.of("I"), Map.of('I', input), -1, 0, false, 0, false);
    }

    public static RecipeDefinition parseSmelting(String id, ConfigurationSection config) {
        String path = "items/recipes.yml: smelting." + id;
        if (!id.matches("[a-z0-9_]+") || config == null) throw new IllegalArgumentException(path + ": invalid recipe");
        for (String key : config.getKeys(false)) {
            if (!Set.of("result", "input", "furnace", "cook-time", "experience", "category").contains(key)) {
                throw new IllegalArgumentException(path + ": unsupported key " + key);
            }
        }
        String result = config.getString("result", "");
        String input = config.getString("input", "");
        String category = config.getString("category", "");
        int time = config.getInt("cook-time", -1);
        double experience = config.getDouble("experience", Double.NaN);
        if (!result.matches("(?:minecraft:)?[a-z0-9_]+") || !input.matches("(?:minecraft:)?[a-z0-9_]+")
                || !category.matches("[a-z0-9_]+") || !Set.of("blast", "furnace").contains(config.getString("furnace", ""))
                || !config.isInt("cook-time") || time < 1 || time > 72000
                || !Double.isFinite(experience) || experience < 0 || experience > 100) {
            throw new IllegalArgumentException(path + ": invalid blast-furnace recipe");
        }
        return new RecipeDefinition(id, result, 1, category, List.of("I"), Map.of('I', input), time, (float) experience, false, 0, "blast".equals(config.getString("furnace")));
    }

    public static RecipeDefinition parse(String id, ConfigurationSection config) {
        String path = "items/recipes.yml: recipes." + id;
        if (!id.matches("[a-z0-9_]+") || config == null) throw new IllegalArgumentException(path + ": invalid recipe");
        for (String key : config.getKeys(false)) {
            if (!Set.of("result", "amount", "category", "shape", "ingredients", "type", "guide-row-offset").contains(key)) {
                throw new IllegalArgumentException(path + ": unsupported key " + key);
            }
        }
        String result = config.getString("result", "");
        String category = config.getString("category", "");
        int amount = config.getInt("amount", 1);
        if (!result.matches("(?:minecraft:)?[a-z0-9_]+") || !category.matches("[a-z0-9_]+")
                || (config.contains("amount") && !config.isInt("amount")) || amount < 1 || amount > 64) {
            throw new IllegalArgumentException(path + ": invalid result, category or amount");
        }
        int offset = config.getInt("guide-row-offset", 0);
        if (config.contains("guide-row-offset") && !config.isInt("guide-row-offset") || offset < 0 || offset > 2) {
            throw new IllegalArgumentException(path + ": invalid guide-row-offset");
        }
        String type = config.getString("type", "shaped");
        if (type.equals("shapeless")) {
            var units = config.getList("ingredients");
            if (config.contains("shape") || units == null || units.isEmpty() || units.size() > 9
                    || units.stream().anyMatch(unit -> !(unit instanceof String value)
                    || !value.matches("(?:#?minecraft:)?[a-z0-9_]+"))) {
                throw new IllegalArgumentException(path + ": shapeless ingredients must be 1–9 item IDs, one per slot");
            }
            var ingredients = new LinkedHashMap<Character, String>();
            var cells = new StringBuilder();
            for (int i = 0; i < units.size(); i++) {
                char symbol = (char) ('A' + i);
                ingredients.put(symbol, (String) units.get(i));
                cells.append(symbol);
            }
            var shape = new java.util.ArrayList<String>();
            while (cells.length() % 3 != 0) cells.append(' ');
            for (int i = 0; i < cells.length(); i += 3) shape.add(cells.substring(i, i + 3));
            if (offset + shape.size() > 3) throw new IllegalArgumentException(path + ": preview exceeds grid");
            return new RecipeDefinition(id, result, amount, category, shape, ingredients, 0, 0, true, offset, false);
        }
        if (!type.equals("shaped")) throw new IllegalArgumentException(path + ": unknown recipe type " + type);
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
            if (symbol.length() != 1 || symbol.equals(" ") || !value.matches("(?:#?minecraft:)?[a-z0-9_]+")) {
                throw new IllegalArgumentException(path + ": invalid ingredient " + symbol);
            }
            ingredients.put(symbol.charAt(0), value);
        }
        var used = new java.util.HashSet<Character>();
        for (String row : shape) for (char symbol : row.toCharArray()) if (symbol != ' ') used.add(symbol);
        if (used.isEmpty() || !used.equals(ingredients.keySet())) {
            throw new IllegalArgumentException(path + ": ingredient symbols must exactly match the shape");
        }
        if (offset + shape.size() > 3) throw new IllegalArgumentException(path + ": preview exceeds grid");
        return new RecipeDefinition(id, result, amount, category, shape, ingredients, 0, 0, false, offset, false);
    }
}
