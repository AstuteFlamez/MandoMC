package net.mandomc.system.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating items and registering recipes.
 *
 * Handles:
 * - Item creation from configuration
 * - Lore building
 * - Persistent data tagging
 * - Crafting and smelting recipe registration
 */
public class ItemFactory {

    private static final Map<String, String> RARITY_ICONS = Map.of(
            "Common", "\u0111",
            "Uncommon", "\u0116",
            "Rare", "\u0115",
            "Epic", "\u0112",
            "Legendary", "\u0113",
            "Mythic", "\u0114"
    );

    private static final Map<String, String> CATEGORY_ICONS = Map.ofEntries(
            Map.entry("Ammo", "\u0123"),
            Map.entry("Armor", "\u0109"),
            Map.entry("Component", "\u010A"),
            Map.entry("Consumable", "\u010B"),
            Map.entry("Key", "\u0124"),
            Map.entry("Material", "\u010C"),
            Map.entry("Metal", "\u010D"),
            Map.entry("Valuable", "\u010E"),
            Map.entry("Vehicle", "\u010F"),
            Map.entry("Weapon", "\u0110"),
            Map.entry("Fuel", "\u0122")
    );

    /*
     * =========================
     * ITEM CREATION
     * =========================
     */

    /**
     * Creates an item from configuration.
     *
     * @param id      the item id
     * @param section the configuration section
     * @return constructed ItemStack (fallback to STONE if invalid)
     */
    public static ItemStack createItem(String id, ConfigurationSection section) {

        Material material = resolveMaterial(id, section);
        if (material == null) return new ItemStack(Material.STONE);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        applyBasicMeta(meta, id, section);
        applyPersistentData(meta, id, section);
        applyLore(meta, section);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Resolves a material from configuration.
     *
     * @param id      the item id
     * @param section the config section
     * @return resolved material or null if invalid
     */
    private static Material resolveMaterial(String id, ConfigurationSection section) {

        String materialName = section.getString("material");

        if (materialName == null) {
            log("Item " + id + " missing material.");
            return null;
        }

        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            log("Invalid material for item " + id + ": " + materialName);
        }

        return material;
    }

    /**
     * Applies basic display metadata.
     *
     * @param meta    the item meta
     * @param id      the item id
     * @param section the config section
     */
    private static void applyBasicMeta(ItemMeta meta, String id, ConfigurationSection section) {

        meta.setDisplayName(color(section.getString("name", "")));

        if (section.contains("custom_model_data")) {
            meta.setCustomModelData(section.getInt("custom_model_data"));
        }
    }

    /**
     * Applies persistent data (id and tags).
     *
     * @param meta    the item meta
     * @param id      the item id
     * @param section the config section
     */
    private static void applyPersistentData(ItemMeta meta, String id, ConfigurationSection section) {

        PersistentDataContainer data = meta.getPersistentDataContainer();

        data.set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id);

        List<String> tags = section.getStringList("tags");
        if (!tags.isEmpty()) {
            data.set(ItemKeys.ITEM_TAGS, PersistentDataType.STRING, String.join(",", tags));
        }
    }

    /**
     * Builds and applies lore to the item.
     *
     * @param meta    the item meta
     * @param section the config section
     */
    private static void applyLore(ItemMeta meta, ConfigurationSection section) {

        List<String> lore = new ArrayList<>();

        String rarity = section.getString("rarity");
        String category = section.getString("category");

        if (rarity != null || category != null) {

            String rarityIcon = RARITY_ICONS.getOrDefault(rarity, "");
            String categoryIcon = CATEGORY_ICONS.getOrDefault(category, "");

            lore.add(color("&f" + rarityIcon + "  " + categoryIcon));
        }

        for (String line : section.getStringList("lore")) {
            lore.add(color(line));
        }

        meta.setLore(lore);
    }

    /*
     * =========================
     * CRAFTING
     * =========================
     */

    /**
     * Registers a shaped crafting recipe.
     *
     * @param id      the recipe id
     * @param result  the resulting item
     * @param section the config section
     */
    public static void registerRecipe(String id, ItemStack result, ConfigurationSection section) {

        if (!section.contains("recipe")) return;

        List<String> shape = section.getStringList("recipe");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");

        if (shape.isEmpty() || ingredients == null) return;

        NamespacedKey key = new NamespacedKey(MandoMC.getInstance(), id);
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        recipe.shape(shape.toArray(new String[0]));

        Map<Character, String> ingredientMap = new HashMap<>();

        for (String symbol : ingredients.getKeys(false)) {

            char keyChar = symbol.charAt(0);
            String value = ingredients.getString(symbol);

            ingredientMap.put(keyChar, value);

            RecipeChoice choice = resolveRecipeChoice(value);
            if (choice != null) {
                recipe.setIngredient(keyChar, choice);
            }
        }

        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);

        RecipeRegistry.registerCrafting(
                new RecipeRegistry.CraftingRecipeData(id, shape, ingredientMap)
        );
    }

    /*
     * =========================
     * SMELTING
     * =========================
     */

    /**
     * Registers a smelting recipe.
     *
     * @param id      the recipe id
     * @param result  the resulting item
     * @param section the config section
     */
    public static void registerSmelting(String id, ItemStack result, ConfigurationSection section) {

        ConfigurationSection smelt = section.getConfigurationSection("smelting");
        if (smelt == null) return;

        String input = smelt.getString("input");
        String furnaceType = smelt.getString("furnace", "furnace");

        if (input == null) return;

        RecipeChoice choice = resolveRecipeChoice(input);
        if (choice == null) return;

        NamespacedKey key = new NamespacedKey(MandoMC.getInstance(), id + "_smelt");

        Bukkit.removeRecipe(key);

        switch (furnaceType.toLowerCase()) {
            case "blast" -> Bukkit.addRecipe(new BlastingRecipe(key, result, choice, 0, 200));
            case "smoker" -> Bukkit.addRecipe(new SmokingRecipe(key, result, choice, 0, 200));
            default -> Bukkit.addRecipe(new FurnaceRecipe(key, result, choice, 0, 200));
        }

        RecipeRegistry.registerSmelting(
                new RecipeRegistry.SmeltingRecipeData(id, input, furnaceType)
        );
    }

    /*
     * =========================
     * HELPERS
     * =========================
     */

    /**
     * Resolves a recipe choice from an item id or material name.
     *
     * @param value item id or material string
     * @return recipe choice or null if invalid
     */
    private static RecipeChoice resolveRecipeChoice(String value) {

        ItemStack custom = ItemRegistry.get(value);
        if (custom != null) {
            return new RecipeChoice.ExactChoice(custom.clone());
        }

        Material mat = Material.matchMaterial(value);
        return mat != null ? new RecipeChoice.MaterialChoice(mat) : null;
    }

    /**
     * Logs a warning message.
     *
     * @param message message to log
     */
    private static void log(String message) {
        MandoMC.getInstance().getLogger().warning(message);
    }

    /**
     * Applies color formatting to text.
     *
     * @param text raw text
     * @return formatted text
     */
    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}