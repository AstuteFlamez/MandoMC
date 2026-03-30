package net.mandomc.system.items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for all crafting and smelting recipes.
 *
 * Stores lightweight representations of recipes for:
 * - GUI display
 * - lookup and validation
 */
public final class RecipeRegistry {

    /**
     * Represents a shaped crafting recipe.
     */
    public static class CraftingRecipeData {

        public final String resultId;
        public final List<String> shape;
        public final Map<Character, String> ingredients;

        /**
         * @param resultId    resulting item id
         * @param shape       crafting grid shape (3 rows)
         * @param ingredients mapping of characters to item/material ids
         */
        public CraftingRecipeData(String resultId,
                                 List<String> shape,
                                 Map<Character, String> ingredients) {

            this.resultId = resultId.toLowerCase();
            this.shape = List.copyOf(shape);
            this.ingredients = Map.copyOf(ingredients);
        }
    }

    /**
     * Represents a smelting recipe.
     */
    public static class SmeltingRecipeData {

        public final String resultId;
        public final String input;
        public final String furnace;

        /**
         * @param resultId resulting item id
         * @param input    input item/material id
         * @param furnace  furnace type (furnace, blast, smoker)
         */
        public SmeltingRecipeData(String resultId,
                                 String input,
                                 String furnace) {

            this.resultId = resultId.toLowerCase();
            this.input = input;
            this.furnace = furnace;
        }
    }

    private static final Map<String, CraftingRecipeData> crafting = new HashMap<>();
    private static final Map<String, SmeltingRecipeData> smelting = new HashMap<>();

    /**
     * Registers a crafting recipe.
     *
     * @param data crafting recipe data
     */
    public static void registerCrafting(CraftingRecipeData data) {
        crafting.put(data.resultId, data);
    }

    /**
     * Registers a smelting recipe.
     *
     * @param data smelting recipe data
     */
    public static void registerSmelting(SmeltingRecipeData data) {
        smelting.put(data.resultId, data);
    }

    /**
     * Checks whether an item has any recipe.
     *
     * @param id item id
     * @return true if crafting or smelting recipe exists
     */
    public static boolean hasRecipe(String id) {
        String key = normalizeId(id);
        return crafting.containsKey(key) || smelting.containsKey(key);
    }

    /**
     * Retrieves crafting recipe data.
     *
     * @param id item id
     * @return crafting recipe or null if not found
     */
    public static CraftingRecipeData getCrafting(String id) {
        return crafting.get(normalizeId(id));
    }

    /**
     * Retrieves smelting recipe data.
     *
     * @param id item id
     * @return smelting recipe or null if not found
     */
    public static SmeltingRecipeData getSmelting(String id) {
        return smelting.get(normalizeId(id));
    }

    /**
     * Returns all item ids that have recipes.
     *
     * @return set of item ids
     */
    public static Set<String> getRecipeItems() {

        Set<String> ids = new HashSet<>();

        ids.addAll(crafting.keySet());
        ids.addAll(smelting.keySet());

        return ids;
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Normalizes item ids to lowercase.
     *
     * @param id raw id
     * @return normalized id
     */
    private static String normalizeId(String id) {
        return id.toLowerCase();
    }

    /**
     * Prevent instantiation.
     */
    private RecipeRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}