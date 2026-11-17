package net.mandomc.gameplay.items;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Groups saber styles by the crystal required for their original crafting recipe. */
public final class RecipeGuideOrder {
    private static final List<String> COLORS = List.of("blue", "green", "red", "purple", "yellow", "orange", "white", "black");
    private final Map<String, RecipeDefinition> recipes = new HashMap<>();

    public RecipeGuideOrder(List<RecipeDefinition> definitions) {
        // Direct crafting takes precedence over a style conversion with the same output.
        definitions.stream().filter(RecipeDefinition::stonecutting).forEach(r -> recipes.putIfAbsent(r.result(), r));
        definitions.stream().filter(r -> !r.stonecutting()).forEach(r -> recipes.put(r.result(), r));
    }

    public String color(RecipeDefinition recipe) { return color(recipe, new HashSet<>()); }

    private String color(RecipeDefinition recipe, Set<String> seen) {
        if (!seen.add(recipe.result())) return "";
        for (String ingredient : recipe.ingredients().values()) {
            if (ingredient.endsWith("_kyber")) return ingredient.substring(0, ingredient.length() - "_kyber".length());
        }
        if (recipe.stonecutting()) {
            var source = recipes.get(recipe.ingredients().get('I'));
            if (source != null) return color(source, seen);
        }
        return "";
    }

    public Comparator<RecipeDefinition> comparator() {
        return Comparator.comparingInt((RecipeDefinition r) -> {
            if (!r.category().equals("lightsabers")) return -1;
            int index = COLORS.indexOf(color(r));
            return index < 0 ? COLORS.size() : index;
        }).thenComparingInt(r -> r.category().equals("lightsabers") && r.stonecutting() ? 1 : 0)
                .thenComparing(r -> r.category().equals("lightsabers") ? r.result() : "");
    }
}
