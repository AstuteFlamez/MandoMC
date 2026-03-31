package net.mandomc.server.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import net.mandomc.gameplay.lightsaber.SaberFactory;
import net.mandomc.gameplay.vehicle.VehicleFactory;
import net.mandomc.gameplay.fuel.FuelFactory;
import net.mandomc.server.items.config.ItemsConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for loading all items from configuration.
 *
 * Uses a two-pass system:
 * - Pass 1: Create and register items
 * - Pass 2: Register crafting and smelting recipes
 */
public final class ItemLoader {

    /**
     * Loads all items and registers their recipes.
     */
    public static void loadItems() {

        Map<String, ConfigurationSection> sections = new HashMap<>();

        loadAndRegisterItems(sections);
        registerRecipes(sections);
    }

    /*
     * =========================
     * PASS 1: ITEM CREATION
     * =========================
     */

    /**
     * Creates items from config and registers them.
     *
     * @param sections map storing item config sections for later use
     */
    private static void loadAndRegisterItems(Map<String, ConfigurationSection> sections) {

        for (Map.Entry<String, FileConfiguration> entry : ItemsConfig.getAll().entrySet()) {

            String category = entry.getKey();
            FileConfiguration config = entry.getValue();

            if (!config.contains("items")) continue;

            ConfigurationSection items = config.getConfigurationSection("items");
            if (items == null) continue;

            for (String id : items.getKeys(false)) {

                ConfigurationSection section = items.getConfigurationSection(id);
                if (section == null) continue;

                ItemStack item = ItemFactory.createItem(id, section);

                List<String> tags = section.getStringList("tags");

                item = applyTagModifiers(item, id, tags);

                String rarity = section.getString("rarity", "Common");

                ItemRegistry.register(id, item, category, rarity, tags);

                sections.put(id, section);
            }
        }
    }

    /*
     * =========================
     * PASS 2: RECIPE REGISTRATION
     * =========================
     */

    /**
     * Registers crafting and smelting recipes for all items.
     *
     * @param sections map of item ids to config sections
     */
    private static void registerRecipes(Map<String, ConfigurationSection> sections) {

        for (Map.Entry<String, ConfigurationSection> entry : sections.entrySet()) {

            String id = entry.getKey();
            ConfigurationSection section = entry.getValue();

            ItemStack item = ItemRegistry.get(id);
            if (item == null) continue;

            ItemFactory.registerRecipe(id, item, section);
            ItemFactory.registerSmelting(id, item, section);
        }
    }

    /*
     * =========================
     * TAG PROCESSING
     * =========================
     */

    /**
     * Applies tag-based modifications to an item.
     *
     * @param item the base item
     * @param id   the item id
     * @param tags list of tags applied to the item
     * @return modified item
     */
    private static ItemStack applyTagModifiers(ItemStack item, String id, List<String> tags) {

        if (tags.contains("SABER")) {
            item = SaberFactory.applyStats(item, id);
        }

        if (tags.contains("VEHICLE")) {
            item = VehicleFactory.applyStats(item, id);
        }

        if (tags.contains("FUEL")) {
            item = FuelFactory.applyStats(item, id);
        }

        return item;
    }

    /**
     * Prevent instantiation.
     */
    private ItemLoader() {
        throw new UnsupportedOperationException("Utility class");
    }
}