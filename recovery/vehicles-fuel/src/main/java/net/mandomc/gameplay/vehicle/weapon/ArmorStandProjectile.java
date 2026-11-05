package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.MandoMC;

import me.deecaad.core.utils.ray.EntityTraceResult;
import me.deecaad.core.utils.ray.RayTraceResult;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScript;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import me.deecaad.core.file.Configuration;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Fires a WeaponMechanics-managed projectile (configured via {@code wm_projectile}
 * in the vehicle YAML) from an arbitrary world location. WM handles the
 * disguise (packet-based FakeEntity), physics, collision detection, and
 * through-block behavior — this class only wires the launch call and
 * attaches a {@link ProjectileScript} for vehicle-specific damage and
 * explosion visuals on impact.
 */
public final class ArmorStandProjectile {

    private static final Random RNG = new Random();

    private ArmorStandProjectile() {}

    /**
     * @param shooter       the player who fired (used for damage attribution)
     * @param origin        world-space firing location (bone pivot or eye)
     * @param direction     normalized direction vector
     * @param config        vehicle weapon config
     * @param vehicleConfigId unused — kept for call-site compatibility
     * @param excluded      UUIDs to exclude from AoE damage
     * @param vehicleEntity the vehicle's backing entity; used as the WM "shooter"
     *                      so its hitbox is excluded from collision for the first
     *                      10 ticks
     */
    public static void launch(Player shooter, Location origin, Vector direction,
                              WeaponConfig config, String vehicleConfigId,
                              Set<UUID> excluded, LivingEntity vehicleEntity) {
        World world = origin.getWorld();
        if (world == null) return;

        String wmTitle = config.getWmProjectile();
        if (wmTitle == null || wmTitle.isBlank()) return;

        Projectile projectileConfig = resolveWmProjectile(wmTitle);
        if (projectileConfig == null) return;

        double speed = Math.max(0.5, config.getProjectileSpeed());
        Vector motion = direction.clone().normalize().multiply(speed);

        // WM excludes the "shooter" entity from its raytrace for the first
        // 10 ticks, which prevents the projectile from colliding with the
        // vehicle model on spawn.
        LivingEntity wmShooter = vehicleEntity != null ? vehicleEntity : shooter;

        WeaponProjectile bullet = projectileConfig.create(
                wmShooter, origin, motion, null, wmTitle, null);

        bullet.addProjectileScript(new VehicleImpactScript(
                MandoMC.getInstance(), bullet, config, excluded, shooter));

        projectileConfig.shoot(bullet, origin);
    }

    // ------------------------------------------------------------------
    //  Impact script — attached per-projectile
    // ------------------------------------------------------------------

    private static class VehicleImpactScript extends ProjectileScript<WeaponProjectile> {

        private final WeaponConfig config;
        private final Set<UUID> excluded;
        private final Player damageSource;
        private boolean collided;

        VehicleImpactScript(org.bukkit.plugin.Plugin owner, WeaponProjectile projectile,
                            WeaponConfig config, Set<UUID> excluded, Player damageSource) {
            super(owner, projectile);
            this.config = config;
            this.excluded = excluded;
            this.damageSource = damageSource;
        }

        @Override
        public void onCollide(RayTraceResult hit) {
            collided = true;

            if (hit instanceof EntityTraceResult entityHit) {
                LivingEntity target = entityHit.getEntity();
                if (target != null && !target.isDead()
                        && !excluded.contains(target.getUniqueId())) {
                    target.damage(config.getDamage(), damageSource);
                }
            }
        }

        @Override
        public void onEnd() {
            if (!collided) return;

            Location impact = projectile.getBukkitLocation();
            applyAoE(impact, config, excluded);
            spawnExplosion(impact, config);
        }
    }

    // ------------------------------------------------------------------
    //  AoE damage
    // ------------------------------------------------------------------

    private static void applyAoE(Location impact, WeaponConfig config,
                                  Set<UUID> excluded) {
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

    // ------------------------------------------------------------------
    //  Multi-phase explosion effect
    // ------------------------------------------------------------------

    /**
     * Expanding explosion → rolling smoke. Five phases spread over ~11 ticks.
     * Uses only EXPLOSION and SMOKE particles.
     */
    private static void spawnExplosion(Location impact, WeaponConfig config) {
        World world = impact.getWorld();
        if (world == null) return;

        double r = Math.max(config.getDamageRadius(), 2.0);

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.4f);

        // Phase 0 — bright core flash
        world.spawnParticle(Particle.EXPLOSION, impact, 4, 0.2, 0.2, 0.2, 0.0);
        spawnSphere(world, impact, r * 0.15, Particle.SMOKE, 15, 0.04);

        // Phase 1 — expanding blast
        Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
            spawnSphere(world, impact, r * 0.3, Particle.EXPLOSION, 6, 0.0);
            spawnSphere(world, impact, r * 0.25, Particle.SMOKE, 25, 0.06);
        }, 2L);

        // Phase 2 — full expansion
        Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
            spawnSphere(world, impact, r * 0.55, Particle.EXPLOSION, 5, 0.0);
            spawnSphere(world, impact, r * 0.5, Particle.SMOKE, 40, 0.1);
        }, 4L);

        // Phase 3 — smoke cloud
        Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
            spawnSphere(world, impact, r * 0.7, Particle.SMOKE, 50, 0.08);
        }, 7L);

        // Phase 4 — lingering smoke plume
        Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
            world.spawnParticle(Particle.SMOKE,
                    impact.clone().add(0, 0.5, 0),
                    25, r * 0.3, 0.8, r * 0.3, 0.02);
        }, 11L);
    }

    // ------------------------------------------------------------------
    //  WM config lookup — tries multiple key patterns
    // ------------------------------------------------------------------

    private static volatile Projectile cachedProjectile;
    private static volatile String cachedTitle;

    /**
     * Tries {@code title}, {@code title.Projectile}, and
     * {@code title + ".Projectile"} as lookup keys. Caches the result so
     * the lookup only happens once per title.
     */
    private static Projectile resolveWmProjectile(String title) {
        if (title.equals(cachedTitle) && cachedProjectile != null) return cachedProjectile;

        Configuration configs = WeaponMechanics.getInstance().getProjectileConfigurations();
        Logger log = MandoMC.getInstance().getLogger();

        // Candidate keys in order of likelihood
        String[] candidates = { title, title + ".Projectile" };
        for (String key : candidates) {
            Projectile p = configs.getObject(key, Projectile.class);
            if (p != null) {
                cachedTitle = title;
                cachedProjectile = p;
                return p;
            }
        }

        // Dump available keys once to help debug
        log.warning("WM projectile '" + title + "' not found under any candidate key.");
        log.warning("Available Projectile entries in WM config:");
        for (Map.Entry<String, Object> entry : configs.entries()) {
            if (entry.getValue() instanceof Projectile) {
                log.warning("  - " + entry.getKey());
            }
        }

        return null;
    }

    private static void spawnSphere(World world, Location center, double radius,
                                    Particle particle, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double theta = RNG.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * RNG.nextDouble() - 1);

            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);

            Location point = center.clone().add(x, y, z);
            world.spawnParticle(particle, point, 1, 0.05, 0.05, 0.05, speed);
        }
    }
}
