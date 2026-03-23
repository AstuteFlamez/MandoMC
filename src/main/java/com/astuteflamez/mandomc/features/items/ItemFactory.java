package com.astuteflamez.mandomc.features.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.MandoMC;

import net.md_5.bungee.api.ChatColor;

import java.util.*;

public class ItemFactory {

    private static final Map<String, String> rarityIcons = Map.of(
            "Common", "\u0111",
            "Uncommon", "\u0116",
            "Rare", "\u0115",
            "Epic", "\u0112",
            "Legendary", "\u0113",
            "Mythic", "\u0114"
    );

    private static final Map<String, String> categoryIcons = Map.ofEntries(
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

    public static ItemStack createItem(String id, ConfigurationSection section) {

        String materialName = section.getString("material");

        if (materialName == null) {
            Bukkit.getLogger().warning("[MandoMC] Item " + id + " missing material.");
            return new ItemStack(Material.STONE);
        }

        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            Bukkit.getLogger().warning("[MandoMC] Invalid material for item " + id + ": " + materialName);
            return new ItemStack(Material.STONE);
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(color(section.getString("name", "")));

        if (section.contains("custom_model_data")) {
            meta.setCustomModelData(section.getInt("custom_model_data"));
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ItemKeys.ITEM_ID, PersistentDataType.STRING, id);

        List<String> tags = section.getStringList("tags");
        if (!tags.isEmpty()) {
            data.set(ItemKeys.ITEM_TAGS, PersistentDataType.STRING, String.join(",", tags));
        }

        List<String> lore = new ArrayList<>();

        String rarity = section.getString("rarity");
        String category = section.getString("category");

        if (rarity != null || category != null) {

            String rarityIcon = rarityIcons.getOrDefault(rarity, "");
            String categoryIcon = categoryIcons.getOrDefault(category, "");

            lore.add(color("&f" + rarityIcon + "  " + categoryIcon));
        }

        if (section.contains("lore")) {
            for (String line : section.getStringList("lore")) {
                lore.add(color(line));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

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

            String value = ingredients.getString(symbol);
            ingredientMap.put(symbol.charAt(0), value);

            RecipeChoice choice;

            ItemStack customItem = ItemRegistry.get(value);

            if (customItem != null) {
                choice = new RecipeChoice.ExactChoice(customItem.clone());
            } else {
                Material mat = Material.matchMaterial(value);
                if (mat == null) continue;
                choice = new RecipeChoice.MaterialChoice(mat);
            }

            recipe.setIngredient(symbol.charAt(0), choice);
        }

        Bukkit.addRecipe(recipe);

        RecipeRegistry.registerCrafting(
                new RecipeRegistry.CraftingRecipeData(id, shape, ingredientMap)
        );
    }

    public static void registerSmelting(String id, ItemStack result, ConfigurationSection section) {

        if (!section.contains("smelting")) return;

        ConfigurationSection smelt = section.getConfigurationSection("smelting");
        if (smelt == null) return;

        String input = smelt.getString("input");
        String furnaceType = smelt.getString("furnace", "furnace");

        if (input == null) return;

        RecipeChoice choice;

        ItemStack customInput = ItemRegistry.get(input);

        if (customInput != null) {
            choice = new RecipeChoice.ExactChoice(customInput.clone());
        } else {
            Material mat = Material.matchMaterial(input);
            if (mat == null) return;
            choice = new RecipeChoice.MaterialChoice(mat);
        }

        NamespacedKey key = new NamespacedKey(MandoMC.getInstance(), id + "_smelt");

        switch (furnaceType.toLowerCase()) {

            case "blast":
                Bukkit.addRecipe(new BlastingRecipe(key, result, choice, 0, 200));
                break;

            case "smoker":
                Bukkit.addRecipe(new SmokingRecipe(key, result, choice, 0, 200));
                break;

            default:
                Bukkit.addRecipe(new FurnaceRecipe(key, result, choice, 0, 200));
        }

        RecipeRegistry.registerSmelting(
                new RecipeRegistry.SmeltingRecipeData(id, input, furnaceType)
        );
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}