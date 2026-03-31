package net.mandomc.gameplay.vehicle.listener;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import net.mandomc.core.modules.server.VehicleModule;

public class DeathListener implements Listener {

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        VehicleModule.unregisterVehicleByEntity(dead.getUniqueId());
    }
}