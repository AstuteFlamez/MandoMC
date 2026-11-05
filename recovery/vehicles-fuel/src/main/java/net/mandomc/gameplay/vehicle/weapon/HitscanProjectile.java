package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Instant-raycast weapon that draws a particle tracer from origin to hit
 * point over several ticks, then triggers an expanding explosion and AoE
 * damage when the tracer arrives.
 * <p>
 * Replaces Bukkit projectile physics for the bone-based weapon path so
 * high-speed shots can never phase through geometry.
 */
public final class HitscanProjectile {

    private static final int EXPLOSION_PHASES = 4;
    private static final int TICKS_BETWEEN_PHASES = 2;
    private static final int POINTS_PER_PHASE = 14;
    private static final Random RNG = new Random();

    private HitscanProjectile() {}

    /**
     * Fires a hitscan shot: raycasts instantly, then animates a visible
     * particle tracer from {@code origin} toward the hit point.
     *
     * @param shooter         the player firing (used for entity-hit exclusion)
     * @param origin          world-space firing location (bone pivot)
     * @param direction       normalized direction vector (vehicle facing)
     * @param config          weapon config (speed, damage, range, particles, etc.)
     * @param vehicleConfigId vehicle config id for impact particle lookup
     * @param excluded        UUIDs to exclude from raytrace and AoE damage
     */
    public static void fire(Player shooter, Location origin, Vector direction,
                            WeaponConfig config, String vehicleConfigId,
                            Set<UUID> excluded) {

        World world = origin.getWorld();
        if (world == null) return;

        double maxRange = config.getMaxRange();

        RayTraceResult result = world.rayTrace(
                origin, direction, maxRange,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                entity -> entity instanceof LivingEntity
                        && !excluded.contains(entity.getUniqueId())
                        && !entity.equals(shooter));

        final Location hitPoint;
        final LivingEntity directHit;

        if (result != null) {
            hitPoint = result.getHitPosition().toLocation(world);
            directHit = (result.getHitEntity() instanceof LivingEntity le) ? le : null;
        } else {
            hitPoint = origin.clone().add(direction.clone().multiply(maxRange));
            directHit = null;
        }

        double distance = origin.distance(hitPoint);
        double speed = Math.max(0.5, config.getProjectileSpeed());
        int totalTicks = Math.max(1, (int) Math.ceil(distance / speed));

        boolean hitSomething = result != null;
        double damage = config.getDamage();

        Vector step = direction.clone().normalize().multiply(speed);

        new BukkitRunnable() {
            int tick = 0;
            final Location head = origin.clone();

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    if (hitSomething) {
                        if (directHit != null && !directHit.isDead()) {
                            directHit.damage(damage, shooter);
                        }
                        applyAoE(hitPoint, config, excluded);
                        spawnExplosion(hitPoint, vehicleConfigId, config);
                    }
                    cancel();
                    return;
                }

                world.spawnParticle(Particle.FLAME, head, 3, 0.05, 0.05, 0.05, 0.001);
                world.spawnParticle(Particle.CRIT, head, 1, 0.02, 0.02, 0.02, 0.0);

                head.add(step);
                tick++;
            }
        }.runTaskTimer(MandoMC.getInstance(), 0L, 1L);
    }

    private static void applyAoE(Location impact, WeaponConfig config, Set<UUID> excluded) {
        double radius = config.getDamageRadius();
        if (radius <= 0) return;

        double damage = config.getDamage();
        if (damage <= 0) return;

        World world = impact.getWorld();
        if (world == null) return;

        for (Entity nearby : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (excluded.contains(living.getUniqueId())) continue;

            living.damage(damage);
        }
    }

    /**
     * Spawns an expanding spherical explosion over several ticks,
     * using impact particles from the vehicle config.
     */
    private static void spawnExplosion(Location impact, String vehicleConfigId,
                                       WeaponConfig config) {
        World world = impact.getWorld();
        if (world == null) return;

        List<WeaponConfig.ParticleEffect> effects = resolveImpactParticles(vehicleConfigId, config);

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);

        double baseRadius = config.getDamageRadius();

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

    private static List<WeaponConfig.ParticleEffect> resolveImpactParticles(
            String vehicleConfigId, WeaponConfig fallback) {

        if (vehicleConfigId != null && !vehicleConfigId.isEmpty()) {
            FileConfiguration vehicleConfig = VehicleConfig.get(vehicleConfigId);
            if (vehicleConfig != null) {
                ConfigurationSection ws = vehicleConfig.getConfigurationSection("vehicle.systems.weapon");
                if (ws != null) {
                    WeaponConfig wc = WeaponConfig.fromSection(ws);
                    if (wc != null && !wc.getImpactParticles().isEmpty()) {
                        return wc.getImpactParticles();
                    }
                }
            }
        }
        return fallback.getImpactParticles();
    }
}
