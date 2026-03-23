package com.astuteflamez.mandomc.content.lightsabers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.system.items.ItemUtils;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;

/**
 * Provides access to lightsaber stat values from item configurations.
 *
 * Retrieves stats such as melee damage, throw damage, and cooldown
 * based on the item's registered id.
 */
public class SaberManager {

    private static final double DEFAULT_MELEE_DAMAGE = 1;
    private static final double DEFAULT_THROW_DAMAGE = 1;
    private static final int DEFAULT_THROW_COOLDOWN = 40;

    /**
     * Retrieves the stats configuration section for an item.
     *
     * @param item the item to read from
     * @return the stats section, or null if not found
     */
    public static ConfigurationSection getStats(ItemStack item) {

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        ConfigurationSection itemSection = ItemsConfig.getItemSection(itemId);
        if (itemSection == null) return null;

        return itemSection.getConfigurationSection("stats");
    }

    /**
     * Gets the melee damage value for a lightsaber item.
     *
     * @param item the item to read from
     * @return melee damage value or default if not defined
     */
    public static double getMeleeDamage(ItemStack item) {
        return getDoubleStat(item, "melee_damage", DEFAULT_MELEE_DAMAGE);
    }

    /**
     * Gets the throw damage value for a lightsaber item.
     *
     * @param item the item to read from
     * @return throw damage value or default if not defined
     */
    public static double getThrowDamage(ItemStack item) {
        return getDoubleStat(item, "throw_damage", DEFAULT_THROW_DAMAGE);
    }

    /**
     * Gets the throw cooldown value for a lightsaber item.
     *
     * @param item the item to read from
     * @return cooldown in ticks or default if not defined
     */
    public static int getThrowCooldown(ItemStack item) {
        return getIntStat(item, "throw_cooldown", DEFAULT_THROW_COOLDOWN);
    }

    /**
     * Retrieves a double stat value from an item.
     *
     * @param item the item to read from
     * @param key the stat key
     * @param def default value
     * @return stat value or default
     */
    private static double getDoubleStat(ItemStack item, String key, double def) {
        ConfigurationSection stats = getStats(item);
        return stats != null ? stats.getDouble(key, def) : def;
    }

    /**
     * Retrieves an integer stat value from an item.
     *
     * @param item the item to read from
     * @param key the stat key
     * @param def default value
     * @return stat value or default
     */
    private static int getIntStat(ItemStack item, String key, int def) {
        ConfigurationSection stats = getStats(item);
        return stats != null ? stats.getInt(key, def) : def;
    }
}