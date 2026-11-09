package net.mandomc.gameplay.vehicles;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record VehicleDefinition(
        String id,
        String name,
        boolean aerial,
        double health,
        double speed,
        double scale,
        double acceleration,
        double braking,
        double pitch,
        double roll,
        double boost,
        int boostTicks,
        int boostCooldown,
        String sound,
        int soundTicks,
        List<String> passengers,
        List<String> gunners) {
    public VehicleDefinition {
        if (!id.matches("[a-z0-9_]+")
                || name.isBlank()
                || !finite(health, 1, 2000)
                || !finite(speed, .05, 4)
                || !finite(scale, .1, 4)
                || !finite(acceleration, .001, 1)
                || !finite(braking, .001, 1)
                || !finite(pitch, 0, 60)
                || !finite(roll, 0, 45)
                || !finite(boost, 1, 2)
                || boostTicks < 0
                || boostTicks > 100
                || boostCooldown < boostTicks
                || boostCooldown > 1200
                || soundTicks < 1
                || soundTicks > 1200
                || passengers.size() + gunners.size() > 8
                || passengers.stream().anyMatch(gunners::contains)) {
            throw new IllegalArgumentException(
                    "vehicles/" + id + ".yml: invalid vehicle parameters");
        }
        passengers = List.copyOf(passengers);
        gunners = List.copyOf(gunners);
    }

    private static boolean finite(double v, double min, double max) {
        return Double.isFinite(v) && v >= min && v <= max;
    }

    public static VehicleDefinition read(String id, ConfigurationSection c) {
        if (c.getInt("schema-version") != 1)
            throw new IllegalArgumentException("vehicles/" + id + ".yml: expected schema 1");
        String movement = c.getString("movement", "");
        if (!List.of("surface", "aerial").contains(movement))
            throw new IllegalArgumentException("Invalid vehicle movement: " + id);
        return new VehicleDefinition(
                id,
                c.getString("name", ""),
                movement.equals("aerial"),
                c.getDouble("health"),
                c.getDouble("speed"),
                c.getDouble("scale"),
                c.getDouble("acceleration"),
                c.getDouble("braking"),
                c.getDouble("max-pitch"),
                c.getDouble("max-roll"),
                c.getDouble("boost.multiplier"),
                c.getInt("boost.duration-ticks"),
                c.getInt("boost.cooldown-ticks"),
                c.getString("sound", ""),
                c.getInt("sound-ticks"),
                c.getStringList("passenger-seats"),
                c.getStringList("gunner-seats"));
    }
}
