package net.mandomc.content.vehicles.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.VehicleData;
import net.mandomc.mechanics.fuel.FuelManager;

/**
 * Manages the heads-up display for players mounted in vehicles.
 *
 * Updates the action bar with fuel status and synchronizes the backing
 * entity's health to reflect the vehicle's current health percentage.
 */
public class VehicleHUDManager {

    private static final Map<UUID, Long> lastUpdate = new HashMap<>();

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
        long last = lastUpdate.getOrDefault(uuid, 0L);

        if (tick - last < 20) return;
        lastUpdate.put(uuid, tick);

        VehicleData data = vehicle.getVehicleData();

        int fuel = FuelManager.getCurrentFuel(data.getItem());
        int maxFuel = FuelManager.getMaxFuel(data.getItem());
        int fuelPercent = (int) ((double) fuel / maxFuel * 100);

        ChatColor fuelColor =
                fuelPercent >= 75 ? ChatColor.GREEN :
                fuelPercent >= 40 ? ChatColor.YELLOW :
                fuelPercent > 0  ? ChatColor.GOLD :
                ChatColor.RED;

        LivingEntity entity = data.getEntity();

        double vehicleHealth = VehicleHealthManager.getCurrentHealth(data.getItem());
        double vehicleMaxHealth = VehicleHealthManager.getMaxHealth(data.getItem());
        double vehiclePercent = vehicleHealth / vehicleMaxHealth;

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
}
