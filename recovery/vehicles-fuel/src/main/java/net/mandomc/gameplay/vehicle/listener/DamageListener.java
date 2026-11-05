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
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;

import java.util.UUID;

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
        Vehicle vehicle = VehicleModule.getVehicleByEntity(damaged.getUniqueId());
        if (vehicle == null) {
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    /**
     * Applies custom vehicle damage for any entity-on-vehicle hit source.
     *
     * Prevents owner self-damage and always routes damage into persistent
     * vehicle item health so despawn/respawn cannot reset damage progress.
     *
     * @param event the entity damage by entity event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDamage(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        Vehicle vehicle = VehicleModule.getVehicleByEntity(damaged.getUniqueId());
        if (vehicle == null) {
            return;
        }

        event.setCancelled(true);

        Player owner = Bukkit.getPlayer(vehicle.getOwnerUUID());
        if (owner == null) {
            return;
        }

        UUID attackerUUID = resolveAttackerUUID(event.getDamager());
        if (attackerUUID != null && attackerUUID.equals(owner.getUniqueId())) {
            return;
        }

        double damage = event.getFinalDamage();
        VehicleData vehicleData = vehicle.getVehicleData();
        ItemStack vehicleItem = vehicleData.getItem();
        VehicleHealthManager.damage(vehicleItem, damage, owner);

        if (damaged instanceof LivingEntity entity) {
            AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                entity.setHealth(maxHealth.getValue());
            }
        }
    }

    private static UUID resolveAttackerUUID(Entity damager) {
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity shooterEntity) {
                return shooterEntity.getUniqueId();
            }
        }
        return damager != null ? damager.getUniqueId() : null;
    }
}
