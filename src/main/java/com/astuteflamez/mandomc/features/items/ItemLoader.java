package com.astuteflamez.mandomc.features.items;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.features.small_features.fuel.FuelFactory;
import com.astuteflamez.mandomc.features.small_features.lightsabers.SaberFactory;
import com.astuteflamez.mandomc.features.vehicles.VehicleFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemLoader {

    public static void loadItems() {

        Map<String, ConfigurationSection> sections = new HashMap<>();

        /*
         * PASS 1: Create and register items
         */
        for (Map.Entry<String, FileConfiguration> entry : ItemsConfig.getAll().entrySet()) {

            String category = entry.getKey();
            FileConfiguration config = entry.getValue();

            if (!config.contains("items")) continue;

            ConfigurationSection items = config.getConfigurationSection("items");

            for (String id : items.getKeys(false)) {

                ConfigurationSection section = items.getConfigurationSection(id);

                ItemStack item = ItemFactory.createItem(id, section);

                /*
                 * TAG SYSTEM
                 */
                List<String> tags = section.getStringList("tags");

                if (tags.contains("SABER")) {
                    item = SaberFactory.applyStats(item, id);
                }
                if (tags.contains("VEHICLE")) {
                    item = VehicleFactory.applyStats(item, id);
                }
                if (tags.contains("FUEL")) {
                    item = FuelFactory.applyStats(item, id);
                } 

                ItemRegistry.register(id, item, category);

                sections.put(id, section);
            }
        }

        /*
         * PASS 2: Register recipes
         */
        for (String id : sections.keySet()) {

            ItemStack item = ItemRegistry.get(id);
            ConfigurationSection section = sections.get(id);

            ItemFactory.registerRecipe(id, item, section);
            ItemFactory.registerSmelting(id, item, section);
        }
    }
}