package com.astuteflamez.mandomc.system.vehicles.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.mechanics.fuel.FuelManager;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;
import com.astuteflamez.mandomc.system.vehicles.VehicleData;
import com.ticxo.modelengine.api.model.ActiveModel;

public class VehicleFuelManager {

    private static final Map<UUID, Long> lastFuelTick = new HashMap<>();

    public static void handleFuel(Player player, Vehicle vehicle, ActiveModel model) {

        UUID uuid = player.getUniqueId();
        long tick = Bukkit.getCurrentTick();

        long last = lastFuelTick.getOrDefault(uuid, 0L);

        // Only burn fuel once per second (20 ticks)
        if (tick - last < 20) return;

        lastFuelTick.put(uuid, tick);

        VehicleData data = vehicle.getVehicleData();

        int fuel = FuelManager.getCurrentFuel(data.getItem());

        fuel--;

        FuelManager.updateFuel(data.getItem(), fuel);

        if (fuel <= 0) {

            player.sendActionBar("§c⚠ Vehicle out of fuel!");

            model.getMountManager().ifPresent(m -> m.dismountDriver());

            VehicleManager.explodeVehicle(player);

            lastFuelTick.remove(uuid);
        }
    }
}