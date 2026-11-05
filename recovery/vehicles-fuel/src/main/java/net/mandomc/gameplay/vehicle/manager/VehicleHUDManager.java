package net.mandomc.gameplay.vehicle.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.fuel.FuelManager;

/**
 * Manages the heads-up display for players mounted in vehicles.
 *
 * Updates the action bar with fuel status and synchronizes the backing
 * entity's health to reflect the vehicle's current health percentage.
 */
public class VehicleHUDManager {

    private static final Map<UUID, Long> lastUpdate = new HashMap<>();
    private static final Map<UUID, TransientActionBar> transientActionBars = new HashMap<>();

    private record TransientActionBar(String message, long untilTick) {}

    /**
     * Updates the HUD for a mounted player.
     *
     * Rate-limited to once per second. Sends fuel percentage to the action bar
     * and syncs the vehicle entity's health to the vehicle's actual health.
     *
     * @param player the player to update
     * @param vehicle the vehicle the player is riding
     */
    public static void updateHUD(Player player, Vehicle vehicle) {
        UUID uuid = player.getUniqueId();
        long tick = Bukkit.getCurrentTick();

        TransientActionBar transientActionBar = transientActionBars.get(uuid);
        if (transientActionBar != null) {
            if (tick <= transientActionBar.untilTick()) {
                player.sendActionBar(transientActionBar.message());
                return;
            }
            transientActionBars.remove(uuid);
        }

        long last = lastUpdate.getOrDefault(uuid, 0L);

        if (tick - last < 20) return;
        lastUpdate.put(uuid, tick);

        VehicleData data = vehicle.getVehicleData();

        int fuel = FuelManager.getCurrentFuel(data.getItem());
        int maxFuel = FuelManager.getMaxFuel(data.getItem());
        int fuelPercent = maxFuel > 0
                ? (int) ((double) fuel / maxFuel * 100)
                : 0;
        if (fuelPercent < 0) {
            fuelPercent = 0;
        }

        ChatColor fuelColor =
                fuelPercent >= 75 ? ChatColor.GREEN :
                fuelPercent >= 40 ? ChatColor.YELLOW :
                fuelPercent > 0  ? ChatColor.GOLD :
                ChatColor.RED;

        LivingEntity entity = data.getEntity();

        double vehicleHealth = VehicleHealthManager.getCurrentHealth(data.getItem());
        double vehicleMaxHealth = VehicleHealthManager.getMaxHealth(data.getItem());
        double vehiclePercent = vehicleMaxHealth > 0 ? (vehicleHealth / vehicleMaxHealth) : 1.0;
        vehiclePercent = Math.max(0.0, Math.min(1.0, vehiclePercent));

        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);

        if (attr != null) {
            double entityMaxHealth = attr.getValue();
            double newHealth = entityMaxHealth * vehiclePercent;
            entity.setHealth(Math.max(1, newHealth));
        }

        player.sendActionBar(
                ChatColor.GRAY + "⛽ Fuel: " + fuelColor + fuelPercent + "%"
        );
    }

    public static void pushTransientActionBar(Player player, String message, int durationTicks) {
        if (player == null || message == null || message.isBlank()) return;
        long now = Bukkit.getCurrentTick();
        long untilTick = now + Math.max(1, durationTicks);
        transientActionBars.put(player.getUniqueId(), new TransientActionBar(message, untilTick));
        player.sendActionBar(message);
    }
}
