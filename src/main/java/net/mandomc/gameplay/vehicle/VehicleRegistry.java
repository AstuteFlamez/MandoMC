package net.mandomc.gameplay.vehicle;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;

/**
 * Maps item ids to vehicle ids.
 *
 * Built from vehicle configs on load and supports full reload.
 */
public final class VehicleRegistry {

    private static final Map<String, String> itemToVehicle = new HashMap<>();

    /**
     * Loads all vehicle-to-item mappings from config.
     *
     * Clears any existing mappings before loading.
     */
    public static void load() {
        clear();

        int loaded = 0;

        for (Map.Entry<String, FileConfiguration> entry : VehicleConfig.getAll().entrySet()) {
            String vehicleId = normalize(entry.getKey());
            FileConfiguration config = entry.getValue();

            String itemId = config.getString("vehicle.item_id");

            if (itemId == null || itemId.isEmpty()) {
                MandoMC.getInstance().getLogger()
                        .warning("Vehicle '" + vehicleId + "' missing item_id.");
                continue;
            }

            itemId = normalize(itemId);

            if (itemToVehicle.containsKey(itemId)) {
                MandoMC.getInstance().getLogger()
                        .warning("Duplicate vehicle mapping for item '" + itemId + "' (overriding previous).");
            }

            itemToVehicle.put(itemId, vehicleId);
            loaded++;
        }

        MandoMC.getInstance().getLogger().info("VehicleRegistry loaded " + loaded + " vehicles.");
    }

    /**
     * Reloads the vehicle registry. Alias for load.
     */
    public static void reload() {
        load();
    }

    /**
     * Clears all vehicle mappings.
     */
    public static void clear() {
        itemToVehicle.clear();
    }

    /**
     * Returns the vehicle id associated with the given item id.
     *
     * @param itemId the item id to look up
     * @return the vehicle id, or null if not mapped
     */
    public static String getVehicleId(String itemId) {
        return itemToVehicle.get(normalize(itemId));
    }

    /**
     * Normalizes an id to lowercase for consistent lookups.
     *
     * @param id the id to normalize
     * @return the lowercase id, or null if the input was null
     */
    private static String normalize(String id) {
        return id == null ? null : id.toLowerCase();
    }

    private VehicleRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}
