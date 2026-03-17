package com.bilal.mandomc.features.vehicles.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.bilal.mandomc.features.small_features.fuel.FuelManager;
import com.bilal.mandomc.features.vehicles.Vehicle;
import com.bilal.mandomc.features.vehicles.VehicleData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VehicleHUDManager {

    private static final Map<UUID, Long> lastUpdate = new HashMap<>();

    public static void updateHUD(Player player, Vehicle vehicle) {

        UUID uuid = player.getUniqueId();
        long tick = Bukkit.getCurrentTick();

        long last = lastUpdate.getOrDefault(uuid, 0L);

        if (tick - last < 20) return; // once per second
        lastUpdate.put(uuid, tick);

        VehicleData data = vehicle.getVehicleData();

        /* ---------------------------
           Fuel
        --------------------------- */

        int fuel = FuelManager.getCurrentFuel(data.getItem());
        int maxFuel = FuelManager.getMaxFuel(data.getItem());

        int fuelPercent = (int) ((double) fuel / maxFuel * 100);

        ChatColor fuelColor =
                fuelPercent >= 75 ? ChatColor.GREEN :
                fuelPercent >= 40 ? ChatColor.YELLOW :
                fuelPercent > 0 ? ChatColor.GOLD :
                ChatColor.RED;

        /* ---------------------------
           Sync entity health
        --------------------------- */

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

        /* ---------------------------
           Action Bar
        --------------------------- */

        player.sendActionBar(
                ChatColor.GRAY + "⛽ Fuel: " + fuelColor + fuelPercent + "%"
        );
    }
}