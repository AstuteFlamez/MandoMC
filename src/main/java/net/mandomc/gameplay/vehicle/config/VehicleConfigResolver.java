package net.mandomc.gameplay.vehicle.config;

import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.SeatType;
import net.mandomc.server.items.ItemUtils;
import net.mandomc.server.items.config.ItemsConfig;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves vehicle configuration values for a given item stack.
 *
 * Looks up the vehicle config via the item id and vehicle registry,
 * then exposes typed accessors for stats, system properties, and seats.
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

    /**
     * Returns the display name used as the vehicle interact GUI title.
     *
     * @param item the vehicle item
     * @return the display name, or an empty string if not configured
     */
    public static String getDisplayName(ItemStack item) {
        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return "";
        return config.getString("vehicle.display_name", "");
    }

    /**
     * Returns the inventory size for the vehicle interact GUI.
     *
     * @param item the vehicle item
     * @return slot count (multiple of 9), defaulting to 27
     */
    public static int getGuiSize(ItemStack item) {
        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return 27;
        return config.getInt("vehicle.gui_size", 27);
    }

    /**
     * Parses and returns the list of seat configurations for the vehicle item.
     *
      * Each seat entry in the YAML should contain: name, slot, type, skull_url.
      * Optional: gunner (boolean) to allow shooting from that seat.
     * Entries with an unrecognised type are skipped.
      *
      * Backward compatibility:
      * - type: GUNNER is treated as PASSENGER + gunner: true
     *
     * @param item the vehicle item
     * @return ordered list of seat configs; empty if none are defined
     */
    public static List<SeatConfig> getSeats(ItemStack item) {
        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return new ArrayList<>();

        List<java.util.Map<?, ?>> rawList = config.getMapList("vehicle.seats");

        List<SeatConfig> seats = new ArrayList<>();

        for (java.util.Map<?, ?> map : rawList) {
            String name     = getString(map, "name", "Seat");
            int    slot     = getInt(map, "slot", 0);
            String skullUrl = getString(map, "skull_url", "");
            String typeRaw  = getString(map, "type", "PASSENGER");
            boolean gunner  = getBoolean(map, "gunner", false)
                    || getBoolean(map, "can_shoot", false);

            SeatType type;
            String normalizedType = typeRaw.toUpperCase();
            if ("GUNNER".equals(normalizedType)) {
                // Legacy support: GUNNER is now a PASSENGER seat with gunner tag.
                type = SeatType.PASSENGER;
                gunner = true;
            } else {
                try {
                    type = SeatType.valueOf(normalizedType);
                } catch (IllegalArgumentException e) {
                    continue; // skip malformed entries
                }
            }

            seats.add(new SeatConfig(name, slot, type, skullUrl, gunner));
        }

        return seats;
    }

    private static String getString(java.util.Map<?, ?> map, String key, String fallback) {
        Object val = map.get(key);
        return val instanceof String s ? s : fallback;
    }

    private static int getInt(java.util.Map<?, ?> map, String key, int fallback) {
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : fallback;
    }

    private static boolean getBoolean(java.util.Map<?, ?> map, String key, boolean fallback) {
        Object val = map.get(key);
        return val instanceof Boolean b ? b : fallback;
    }
}
