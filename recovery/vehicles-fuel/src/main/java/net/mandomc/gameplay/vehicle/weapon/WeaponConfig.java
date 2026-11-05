package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.server.items.ItemUtils;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable weapon configuration parsed from a vehicle's YAML file.
 * All weapon behavior is driven by these values; no per-vehicle Java class needed.
 */
public class WeaponConfig {

    public enum ProjectileType {
        ARROW,
        FIREBALL,
        SNOWBALL,
        CUSTOM
    }

    public record ParticleEffect(
            Particle particle,
            int count,
            double spread,
            double speed
    ) {}

    private final ProjectileType projectileType;
    private final double damage;
    private final double projectileSpeed;
    private final boolean gravity;
    private final long cooldownMs;
    private final int burst;
    private final int burstIntervalTicks;
    private final double spreadDegrees;
    private final String ammo;
    private final int ammoPerShot;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;
    private final List<ParticleEffect> particles;
    private final List<String> weaponBones;
    private final double damageRadius;
    private final List<ParticleEffect> impactParticles;
    private final double maxRange;
    private final String wmProjectile;

    private WeaponConfig(ProjectileType projectileType, double damage,
                         double projectileSpeed, boolean gravity,
                         long cooldownMs, int burst, int burstIntervalTicks,
                         double spreadDegrees, String ammo, int ammoPerShot,
                         String sound, float soundVolume, float soundPitch,
                         List<ParticleEffect> particles,
                         List<String> weaponBones, double damageRadius,
                         List<ParticleEffect> impactParticles, double maxRange,
                         String wmProjectile) {
        this.projectileType = projectileType;
        this.damage = damage;
        this.projectileSpeed = projectileSpeed;
        this.gravity = gravity;
        this.cooldownMs = cooldownMs;
        this.burst = burst;
        this.burstIntervalTicks = burstIntervalTicks;
        this.spreadDegrees = spreadDegrees;
        this.ammo = ammo;
        this.ammoPerShot = ammoPerShot;
        this.sound = sound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.particles = Collections.unmodifiableList(particles);
        this.weaponBones = Collections.unmodifiableList(weaponBones);
        this.damageRadius = damageRadius;
        this.impactParticles = Collections.unmodifiableList(impactParticles);
        this.maxRange = maxRange;
        this.wmProjectile = wmProjectile;
    }

    /**
     * Resolves a WeaponConfig from a vehicle item's configuration.
     * Returns null if the item has no weapon section.
     */
    public static WeaponConfig fromItem(ItemStack item) {
        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return null;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return null;

        ConfigurationSection section = config.getConfigurationSection("vehicle.systems.weapon");
        if (section == null) return null;

        return fromSection(section);
    }

    /**
     * Parses a WeaponConfig from a YAML ConfigurationSection.
     */
    public static WeaponConfig fromSection(ConfigurationSection section) {
        if (section == null) return null;

        ProjectileType type = parseProjectileType(section.getString("projectile_type", "ARROW"));
        double damage = section.getDouble("damage", 6.0);
        double speed = section.getDouble("speed", 2.0);
        boolean gravity = section.getBoolean("gravity", false);
        long cooldown = section.getLong("cooldown", 0);
        int burst = section.getInt("burst", 1);
        int burstInterval = section.getInt("burst_interval", 0);
        double spread = section.getDouble("spread", 0);
        String ammo = section.getString("ammo");
        int ammoPerShot = section.getInt("ammo_per_shot", 1);
        String sound = section.getString("sound");
        float soundVolume = (float) section.getDouble("sound_volume", 1.0);
        float soundPitch = (float) section.getDouble("sound_pitch", 1.0);

        List<ParticleEffect> particles = parseParticleList(section, "particles");

        List<String> weaponBones = section.getStringList("weapon_bones");
        double damageRadius = section.getDouble("damage_radius", 0.0);
        List<ParticleEffect> impactParticles = parseParticleList(section, "impact_particles");
        double maxRange = section.getDouble("max_range", 100.0);
        String wmProjectile = section.getString("wm_projectile");

        return new WeaponConfig(type, damage, speed, gravity,
                cooldown, burst, burstInterval, spread,
                ammo, ammoPerShot, sound, soundVolume, soundPitch,
                particles, weaponBones, damageRadius, impactParticles, maxRange,
                wmProjectile);
    }

    private static ProjectileType parseProjectileType(String value) {
        if (value == null) return ProjectileType.ARROW;
        try {
            return ProjectileType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ProjectileType.ARROW;
        }
    }

    private static List<ParticleEffect> parseParticleList(ConfigurationSection weaponSection, String key) {
        List<?> rawList = weaponSection.getList(key);
        if (rawList == null) return List.of();

        List<ParticleEffect> effects = new ArrayList<>();
        for (Object entry : rawList) {
            if (!(entry instanceof java.util.Map<?, ?> map)) continue;

            Object typeObj = map.get("type");
            String typeStr = typeObj != null ? String.valueOf(typeObj) : "FLAME";
            Particle particle;
            try {
                particle = Particle.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }

            int count = map.containsKey("count") ? ((Number) map.get("count")).intValue() : 20;
            double spread = map.containsKey("spread") ? ((Number) map.get("spread")).doubleValue() : 0.1;
            double speed = map.containsKey("speed") ? ((Number) map.get("speed")).doubleValue() : 0.5;

            effects.add(new ParticleEffect(particle, count, spread, speed));
        }
        return effects;
    }

    // --- Accessors ---

    public ProjectileType getProjectileType() { return projectileType; }
    public double getDamage() { return damage; }
    public double getProjectileSpeed() { return projectileSpeed; }
    public boolean hasGravity() { return gravity; }
    public long getCooldownMs() { return cooldownMs; }
    public int getBurst() { return burst; }
    public int getBurstIntervalTicks() { return burstIntervalTicks; }
    public double getSpreadDegrees() { return spreadDegrees; }
    public String getAmmo() { return ammo; }
    public int getAmmoPerShot() { return ammoPerShot; }
    public String getSound() { return sound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
    public List<ParticleEffect> getParticles() { return particles; }
    public List<String> getWeaponBones() { return weaponBones; }
    public double getDamageRadius() { return damageRadius; }
    public List<ParticleEffect> getImpactParticles() { return impactParticles; }
    public double getMaxRange() { return maxRange; }
    public String getWmProjectile() { return wmProjectile; }
}
