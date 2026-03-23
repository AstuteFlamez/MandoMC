package com.astuteflamez.mandomc.system.items;

import java.util.*;

public class RecipeRegistry {

    public static class CraftingRecipeData {

        public final String resultId;
        public final List<String> shape;
        public final Map<Character, String> ingredients;

        public CraftingRecipeData(String resultId, List<String> shape, Map<Character, String> ingredients) {
            this.resultId = resultId;
            this.shape = shape;
            this.ingredients = ingredients;
        }
    }

    public static class SmeltingRecipeData {

        public final String resultId;
        public final String input;
        public final String furnace;

        public SmeltingRecipeData(String resultId, String input, String furnace) {
            this.resultId = resultId;
            this.input = input;
            this.furnace = furnace;
        }
    }

    private static final Map<String, CraftingRecipeData> crafting = new HashMap<>();
    private static final Map<String, SmeltingRecipeData> smelting = new HashMap<>();

    public static void registerCrafting(CraftingRecipeData data) {
        crafting.put(data.resultId, data);
    }

    public static void registerSmelting(SmeltingRecipeData data) {
        smelting.put(data.resultId, data);
    }

    public static boolean hasRecipe(String id) {
        return crafting.containsKey(id) || smelting.containsKey(id);
    }

    public static CraftingRecipeData getCrafting(String id) {
        return crafting.get(id);
    }

    public static SmeltingRecipeData getSmelting(String id) {
        return smelting.get(id);
    }

    public static Set<String> getRecipeItems() {

        Set<String> ids = new HashSet<>();

        ids.addAll(crafting.keySet());
        ids.addAll(smelting.keySet());

        return ids;
    }
}