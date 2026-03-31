package net.mandomc.gameplay.vehicle.listener;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.core.modules.server.VehicleModule;

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