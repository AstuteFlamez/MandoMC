package net.mandomc.system.vehicles.config;

import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.vehicles.VehicleRegistry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

public class VehicleConfigResolver {

    /* =========================
       INTERNAL HELPERS
    ========================= */

    private static FileConfiguration getVehicleConfig(ItemStack item) {

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return null;

        return VehicleConfig.get(vehicleId);
    }

    private static ConfigurationSection getStats(ItemStack item) {

        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;

        return config.getConfigurationSection("vehicle.stats");
    }

    private static ConfigurationSection getSystems(ItemStack item) {

        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;

        return config.getConfigurationSection("vehicle.systems");
    }

    /* =========================
       STATS
    ========================= */

    public static double getSpeed(ItemStack item) {

        ConfigurationSection stats = getStats(item);
        if (stats == null) return 40;

        return stats.getDouble("speed", 40);
    }

    public static double getScale(ItemStack item) {

        ConfigurationSection stats = getStats(item);
        if (stats == null) return 2;

        return stats.getDouble("scale", 2);
    }

    public static String getModelKey(ItemStack item) {

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return "";

        var section = ItemsConfig.getItemSection(itemId);
        if (section == null) return "";

        return section.getString("model_key", "");
    }

    /* =========================
       SYSTEMS
    ========================= */

    public static String getMovementSound(ItemStack item) {

        ConfigurationSection systems = getSystems(item);
        if (systems == null) return null;

        return systems.getString("movement_sound");
    }

    public static int getMovementSoundLength(ItemStack item) {

        ConfigurationSection systems = getSystems(item);
        if (systems == null) return 0;

        return systems.getInt("movement_sound_time_in_ticks");
    }
}