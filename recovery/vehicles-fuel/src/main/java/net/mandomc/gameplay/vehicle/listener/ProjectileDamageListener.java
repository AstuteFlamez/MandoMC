package net.mandomc.gameplay.vehicle.listener;

import net.mandomc.MandoMC;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.weapon.ProjectileSpawner;
import net.mandomc.gameplay.vehicle.weapon.WeaponConfig;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handles hit events for vehicle projectiles.
 * Applies configured damage on direct hit and AoE damage with impact
 * particles when a damage radius is configured.
 */
public class ProjectileDamageListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!isVehicleProjectile(projectile)) return;

        Location impact = projectile.getLocation();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();

        applyAoE(projectile, impact, pdc);
        spawnImpactParticles(impact, pdc);

        projectile.remove();
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
    }

    private void applyAoE(Projectile projectile, Location impact, PersistentDataContainer pdc) {
        Double radius = pdc.get(ProjectileSpawner.VEHICLE_DAMAGE_RADIUS_KEY, PersistentDataType.DOUBLE);
        if (radius == null || radius <= 0) return;

        Double damage = pdc.get(ProjectileSpawner.VEHICLE_DAMAGE_KEY, PersistentDataType.DOUBLE);
        if (damage == null || damage <= 0) return;

        Set<UUID> excluded = resolveExcluded(projectile);
        World world = impact.getWorld();
        if (world == null) return;

        for (Entity nearby : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (excluded.contains(living.getUniqueId())) continue;
            if (nearby.equals(projectile)) continue;

            living.damage(damage);
        }
    }

    /**
     * Builds the set of UUIDs that should be excluded from AoE damage:
     * the shooter and all occupants of the shooter's vehicle.
     */
    private Set<UUID> resolveExcluded(Projectile projectile) {
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player shooter)) return Set.of();

        UUID shooterUUID = shooter.getUniqueId();
        Vehicle vehicle = VehicleModule.getVehicleForPlayer(shooterUUID);
        if (vehicle == null) return Set.of(shooterUUID);

        var occupants = vehicle.getOccupants();
        var excluded = new java.util.HashSet<>(occupants.keySet());
        excluded.add(shooterUUID);
        excluded.add(vehicle.getOwnerUUID());
        return excluded;
    }

    private static final int EXPLOSION_PHASES = 4;
    private static final int TICKS_BETWEEN_PHASES = 2;
    private static final int POINTS_PER_PHASE = 14;
    private static final Random RNG = new Random();

    /**
     * Spawns an expanding spherical explosion effect over several ticks.
     * Phase 0 is a tight bright core; each subsequent phase pushes particles
     * outward along random directions on a growing shell radius.
     */
    private void spawnImpactParticles(Location impact, PersistentDataContainer pdc) {
        String configId = pdc.get(ProjectileSpawner.VEHICLE_CONFIG_ID_KEY, PersistentDataType.STRING);
        if (configId == null || configId.isEmpty()) return;

        FileConfiguration vehicleConfig = VehicleConfig.get(configId);
        if (vehicleConfig == null) return;

        ConfigurationSection weaponSection = vehicleConfig.getConfigurationSection("vehicle.systems.weapon");
        if (weaponSection == null) return;

        WeaponConfig wc = WeaponConfig.fromSection(weaponSection);
        if (wc == null) return;

        List<WeaponConfig.ParticleEffect> effects = wc.getImpactParticles();
        if (effects.isEmpty()) return;

        World world = impact.getWorld();
        if (world == null) return;

        double baseRadius = wc.getDamageRadius();

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);

        for (int phase = 0; phase < EXPLOSION_PHASES; phase++) {
            final int p = phase;
            long delay = (long) phase * TICKS_BETWEEN_PHASES;

            Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
                double progress = (double) (p + 1) / EXPLOSION_PHASES;
                double shellRadius = baseRadius * progress * 0.8;

                for (WeaponConfig.ParticleEffect effect : effects) {
                    for (int i = 0; i < POINTS_PER_PHASE; i++) {
                        double theta = RNG.nextDouble() * 2 * Math.PI;
                        double phi = Math.acos(2 * RNG.nextDouble() - 1);

                        double x = shellRadius * Math.sin(phi) * Math.cos(theta);
                        double y = shellRadius * Math.sin(phi) * Math.sin(theta);
                        double z = shellRadius * Math.cos(phi);

                        Location point = impact.clone().add(x, y, z);
                        world.spawnParticle(
                                effect.particle(), point,
                                Math.max(1, effect.count() / EXPLOSION_PHASES),
                                0.15, 0.15, 0.15,
                                effect.speed());
                    }
                }

                if (p == 0) {
                    world.spawnParticle(Particle.EXPLOSION, impact, 2, 0.3, 0.3, 0.3, 0.0);
                }
            }, delay);
        }
    }

    private boolean isVehicleProjectile(Projectile projectile) {
        return projectile.getPersistentDataContainer()
                .has(ProjectileSpawner.VEHICLE_PROJECTILE_KEY, PersistentDataType.BOOLEAN);
    }
}
