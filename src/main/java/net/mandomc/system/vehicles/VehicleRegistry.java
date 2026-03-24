package net.mandomc.system.vehicles;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import net.mandomc.MandoMC;
import net.mandomc.system.vehicles.config.VehiclesConfig;

/**
 * Maps item ids -> vehicle ids.
 *
 * Built from vehicle configs and supports full reload.
 */
public final class VehicleRegistry {

    private static final Map<String, String> itemToVehicle = new HashMap<>();

    /* =====================================================
       LOAD / RELOAD
    ===================================================== */

    public static void load() {

        clear();

        int loaded = 0;

        for (Map.Entry<String, FileConfiguration> entry : VehiclesConfig.getAll().entrySet()) {

            String vehicleId = normalize(entry.getKey());
            FileConfiguration config = entry.getValue();

            String itemId = config.getString("vehicle.item_id");

            if (itemId == null || itemId.isEmpty()) {
                MandoMC.getInstance().getLogger()
                        .warning("Vehicle '" + vehicleId + "' missing item_id.");
                continue;
            }

            itemId = normalize(itemId);

            // ⚠️ Prevent silent overrides
            if (itemToVehicle.containsKey(itemId)) {
                MandoMC.getInstance().getLogger()
                        .warning("Duplicate vehicle mapping for item '" + itemId +
                                "' (overriding previous).");
            }

            itemToVehicle.put(itemId, vehicleId);
            loaded++;
        }

        MandoMC.getInstance().getLogger()
                .info("VehicleRegistry loaded " + loaded + " vehicles.");
    }

    /**
     * Reloads the vehicle registry.
     *
     * Alias for load() for consistency with other systems.
     */
    public static void reload() {
        load();
    }

    /* =====================================================
       CLEAR
    ===================================================== */

    public static void clear() {
        itemToVehicle.clear();
    }

    /* =====================================================
       GETTERS
    ===================================================== */

    public static String getVehicleId(String itemId) {
        return itemToVehicle.get(normalize(itemId));
    }

    /* =====================================================
       HELPERS
    ===================================================== */

    private static String normalize(String id) {
        return id == null ? null : id.toLowerCase();
    }

    private VehicleRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }
}