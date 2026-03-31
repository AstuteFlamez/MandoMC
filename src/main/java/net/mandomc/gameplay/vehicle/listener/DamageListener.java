package net.mandomc.gameplay.vehicle.listener;

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

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.manager.VehicleHealthManager;
import net.mandomc.core.modules.server.VehicleModule;

/**
 * Handles damage events for active vehicles.
 *
 * Cancels environmental damage to vehicles and routes player-inflicted
 * damage through the custom vehicle health system.
 */
public class DamageListener implements Listener {

    /**
     * Cancels all environmental damage (fall, fire, etc.) to active vehicles.
     *
     * @param event the entity damage event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        Entity damaged = event.getEntity();

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {
            VehicleData vehicleData = vehicle.getVehicleData();
            Entity vehicleEntity = vehicleData.getEntity();

            if (vehicleEntity == null) continue;
            if (!vehicleEntity.getUniqueId().equals(damaged.getUniqueId())) continue;

            if (!(event instanceof EntityDamageByEntityEvent)) {
                event.setCancelled(true);
            }

            return;
        }
    }

    /**
     * Applies custom vehicle damage when a player attacks a vehicle.
     *
     * Prevents the vehicle owner from damaging their own vehicle.
     * Delegates damage calculation to VehicleHealthManager.
     *
     * @param event the entity damage by entity event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {
            VehicleData vehicleData = vehicle.getVehicleData();
            Entity vehicleEntity = vehicleData.getEntity();

            if (vehicleEntity == null) continue;
            if (!vehicleEntity.getUniqueId().equals(damaged.getUniqueId())) continue;

            Player owner = Bukkit.getPlayer(vehicle.getOwnerUUID());

            if (owner != null && damager.getUniqueId().equals(owner.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (!(damager instanceof Player player)) return;

            double damage = event.getFinalDamage();
            ItemStack vehicleItem = vehicleData.getItem();

            VehicleHealthManager.damage(vehicleItem, damage, player);

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
