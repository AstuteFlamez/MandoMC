package net.mandomc.gameplay.vehicle.listener;

import net.mandomc.gameplay.vehicle.weapon.ProjectileSpawner;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles hit events for vehicle projectiles.
 * Applies configured damage and removes projectiles on block contact.
 */
public class ProjectileDamageListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!isVehicleProjectile(projectile)) return;

        if (event.getHitBlock() != null) {
            projectile.remove();
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Projectile projectile)) return;
        if (!isVehicleProjectile(projectile)) return;

        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        Double damage = pdc.get(ProjectileSpawner.VEHICLE_DAMAGE_KEY, PersistentDataType.DOUBLE);

        if (damage != null && damage > 0) {
            event.setDamage(damage);
        }

        projectile.remove();
    }

    private boolean isVehicleProjectile(Projectile projectile) {
        return projectile.getPersistentDataContainer()
                .has(ProjectileSpawner.VEHICLE_PROJECTILE_KEY, PersistentDataType.BOOLEAN);
    }
}
