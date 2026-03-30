package net.mandomc.content.vehicles.config;

import net.mandomc.content.vehicles.VehicleRegistry;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.config.ItemsConfig;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves vehicle configuration values for a given item stack.
 *
 * Looks up the vehicle config via the item id and vehicle registry,
 * then exposes typed accessors for stats and system properties.
 */
public class VehicleConfigResolver {

    /**
     * Returns the vehicle config for the given item, or null if unavailable.
     */
    private static FileConfiguration getVehicleConfig(ItemStack item) {
        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return null;

        return VehicleConfig.get(vehicleId);
    }

    /**
     * Returns the stats configuration section for the item's vehicle.
     */
    private static ConfigurationSection getStats(ItemStack item) {
        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;
        return config.getConfigurationSection("vehicle.stats");
    }

    /**
     * Returns the systems configuration section for the item's vehicle.
     */
    private static ConfigurationSection getSystems(ItemStack item) {
        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;
        return config.getConfigurationSection("vehicle.systems");
    }

    /**
     * Returns the configured speed for the vehicle item.
     *
     * @param item the vehicle item
     * @return speed value, defaulting to 40
     */
    public static double getSpeed(ItemStack item) {
        ConfigurationSection stats = getStats(item);
        if (stats == null) return 40;
        return stats.getDouble("speed", 40);
    }

    /**
     * Returns the configured scale for the vehicle item.
     *
     * @param item the vehicle item
     * @return scale value, defaulting to 2
     */
    public static double getScale(ItemStack item) {
        ConfigurationSection stats = getStats(item);
        if (stats == null) return 2;
        return stats.getDouble("scale", 2);
    }

    /**
     * Returns the ModelEngine model key for the vehicle item.
     *
     * @param item the vehicle item
     * @return model key string, or empty string if not configured
     */
    public static String getModelKey(ItemStack item) {
        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return "";

        var section = ItemsConfig.getItemSection(itemId);
        if (section == null) return "";

        return section.getString("model_key", "");
    }

    /**
     * Returns the movement sound identifier for the vehicle item.
     *
     * @param item the vehicle item
     * @return the sound identifier, or null if not configured
     */
    public static String getMovementSound(ItemStack item) {
        ConfigurationSection systems = getSystems(item);
        if (systems == null) return null;
        return systems.getString("movement_sound");
    }

    /**
     * Returns the movement sound length for the vehicle item in ticks.
     *
     * @param item the vehicle item
     * @return sound duration in ticks, or 0 if not configured
     */
    public static int getMovementSoundLength(ItemStack item) {
        ConfigurationSection systems = getSystems(item);
        if (systems == null) return 0;
        return systems.getInt("movement_sound_time_in_ticks");
    }
}
