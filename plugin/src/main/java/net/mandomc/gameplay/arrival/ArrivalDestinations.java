package net.mandomc.gameplay.arrival;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Uses the world's configured spawn, so Multiverse remains the location authority. */
public final class ArrivalDestinations {
    private final ArrivalSettings settings;
    private final ArrivalProtection protection;

    public ArrivalDestinations(ArrivalSettings settings, ArrivalProtection protection) {
        this.settings = settings;
        this.protection = protection;
    }

    public Location spawn() {
        return resolve(settings.spawnWorld());
    }

    public Location survival() {
        return resolve(settings.survivalWorld());
    }

    public void validate() {
        spawn();
        survival();
    }

    private Location resolve(String key) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            throw new IllegalStateException("Multiverse-Core must be enabled for arrival destinations");
        }
        var world = Bukkit.getWorld(NamespacedKey.fromString(key));
        if (world == null) {
            throw new IllegalStateException("config.yml: arrival world " + key + " is not loaded");
        }
        var location = world.getSpawnLocation().add(0.5, 0, 0.5);
        if (!world.getWorldBorder().isInside(location)) {
            throw new IllegalStateException("Configured spawn is outside the world border: " + key);
        }
        // Inspect every block touched by a standing player's footprint, not just its center.
        for (double dx : new double[] {-0.3, 0.3}) {
            for (double dz : new double[] {-0.3, 0.3}) {
                var feet = location.clone().add(dx, 0, dz);
                var floor = feet.clone().subtract(0, 1, 0).getBlock();
                var type = floor.getType();
                if (!type.isSolid() || !floor.getCollisionShape().getBoundingBoxes().stream()
                        .anyMatch(box -> box.getMaxY() >= 1.0)
                        || type.name().contains("MAGMA") || type.name().contains("CACTUS")
                        || type.name().contains("CAMPFIRE")
                        || !feet.getBlock().isEmpty() || !feet.clone().add(0, 1, 0).getBlock().isEmpty()) {
                    throw new IllegalStateException("Unsafe arrival in " + key + " at "
                            + location.getX() + "," + location.getY() + "," + location.getZ()
                            + " (floor=" + type + ", feet=" + feet.getBlock().getType() + ")");
                }
                protection.requireProtected(feet);
            }
        }
        return location;
    }
}
