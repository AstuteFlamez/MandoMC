package net.mandomc.gameplay.vehicles;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** Recovered cannon values, separate from movement and held-blaster policy. */
public record VehicleWeaponSettings(double damage, double speed, int cooldownMillis, String ammo,
        int ammoPerProjectile, List<String> bones, double range, double radius, String sound, boolean terrainExplosion) {
    public VehicleWeaponSettings(double damage, double speed, int cooldownMillis, String ammo,
            int ammoPerProjectile, List<String> bones, double range, double radius, String sound) {
        this(damage, speed, cooldownMillis, ammo, ammoPerProjectile, bones, range, radius, sound, false);
    }

    public VehicleWeaponSettings {
        bones = List.copyOf(bones);
        if (!Double.isFinite(damage) || damage <= 0 || damage > 100
                || !Double.isFinite(speed) || speed <= 0 || speed > 8
                || cooldownMillis < 0 || cooldownMillis > 60000
                || ammo == null || !ammo.matches("[a-z0-9_]+")
                || ammoPerProjectile < 1 || ammoPerProjectile > 64
                || bones.isEmpty() || bones.size() > 8 || ammoPerProjectile * bones.size() > 64 || bones.stream().distinct().count() != bones.size()
                || bones.stream().anyMatch(b -> !b.matches("[a-z0-9_]+"))
                || !Double.isFinite(range) || range < 1 || range > 256
                || !Double.isFinite(radius) || radius < 0 || radius > 16
                || sound == null || sound.isBlank()) throw new IllegalArgumentException("vehicles/weapons.yml: invalid cannon");
    }

    public int ammoCost() { return ammoPerProjectile * bones.size(); }

    /** Tibanna's bolt follows ammunition identity, so changing a vehicle's ammo cannot leave a stale color. */
    public org.bukkit.NamespacedKey projectileModel() {
        return new org.bukkit.NamespacedKey("mandomc", "tibanna_gas_cells".equals(ammo)
                ? "projectiles/greenlaser" : "projectiles/redlaser");
    }

    public static VehicleWeaponSettings read(ConfigurationSection c) {
        if (c == null) throw new IllegalArgumentException("vehicles/weapons.yml: missing vehicle");
        return new VehicleWeaponSettings(c.getDouble("damage"), c.getDouble("speed"), c.getInt("cooldown-ms"),
                c.getString("ammo"), c.getInt("ammo-per-projectile"), c.getStringList("bones"),
                c.getDouble("range"), c.getDouble("damage-radius"), c.getString("sound"),
                c.getBoolean("terrain-explosion", "proton_torpedo".equals(c.getString("ammo"))));
    }
}
