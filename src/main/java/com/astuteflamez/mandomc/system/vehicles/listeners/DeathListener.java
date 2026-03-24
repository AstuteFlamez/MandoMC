package com.astuteflamez.mandomc.system.vehicles.listeners;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import com.astuteflamez.mandomc.modules.system.VehicleModule;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;

public class DeathListener implements Listener {

    @EventHandler
    public void onDeath(EntityDeathEvent event) {

        Entity dead = event.getEntity();

        Iterator<Map.Entry<UUID, Vehicle>> iterator = VehicleModule.getActiveVehicles().entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<UUID, Vehicle> entry = iterator.next();
            Vehicle vehicle = entry.getValue();

            Entity vehicleEntity = vehicle.getVehicleData().getEntity();

            if (vehicleEntity.getUniqueId().equals(dead.getUniqueId())) {

                // Remove the player → vehicle mapping
                iterator.remove();

                return;
            }
        }
    }
}