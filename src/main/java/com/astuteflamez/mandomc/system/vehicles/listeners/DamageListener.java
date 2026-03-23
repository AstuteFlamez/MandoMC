package com.astuteflamez.mandomc.system.vehicles.listeners;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;
import com.astuteflamez.mandomc.system.vehicles.VehicleData;
import com.astuteflamez.mandomc.system.vehicles.managers.VehicleHealthManager;

public class DamageListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnvironmentalDamage(EntityDamageEvent event) {

        Entity damaged = event.getEntity();

        for (Vehicle vehicle : MandoMC.activeVehicles.values()) {

            VehicleData vehicleData = vehicle.getVehicleData();
            Entity vehicleEntity = vehicleData.getEntity();

            if (vehicleEntity == null) continue;

            // Only process if the damaged entity IS the vehicle
            if (!vehicleEntity.getUniqueId().equals(damaged.getUniqueId())) continue;

            // Cancel environmental damage (fall, lava, fire, etc.)
            if (!(event instanceof EntityDamageByEntityEvent)) {
                event.setCancelled(true);
            }

            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        for (Vehicle vehicle : MandoMC.activeVehicles.values()) {

            VehicleData vehicleData = vehicle.getVehicleData();
            Entity vehicleEntity = vehicleData.getEntity();

            if (vehicleEntity == null) continue;

            // Only process if this entity is the vehicle
            if (!vehicleEntity.getUniqueId().equals(damaged.getUniqueId())) continue;

            Player owner = Bukkit.getPlayer(vehicle.getOwnerUUID());

            // Owner cannot damage their own vehicle
            if (owner != null && damager.getUniqueId().equals(owner.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (!(damager instanceof Player player)) return;

            double damage = event.getFinalDamage();

            ItemStack vehicleItem = vehicleData.getItem();

            /* -----------------------
               Apply custom vehicle damage
            ----------------------- */

            VehicleHealthManager.damage(vehicleItem, damage, player);

            double health = VehicleHealthManager.getCurrentHealth(vehicleItem);

            /* -----------------------
               Prevent entity from dying
            ----------------------- */

            if (damaged instanceof LivingEntity entity) {

                AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);

                if (maxHealth != null) {
                    entity.setHealth(maxHealth.getValue());
                }
            }

            return;
        }
    }
}