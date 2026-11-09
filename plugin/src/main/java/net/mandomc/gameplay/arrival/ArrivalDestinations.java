package net.mandomc.gameplay.arrival;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Validates arrivals from the single MandoMC world registry. */
public final class ArrivalDestinations {
    private final ArrivalSettings settings;
    private final ArrivalProtection protection;
    private final java.util.function.Function<String, Location> spawns;

    public ArrivalDestinations(ArrivalSettings settings, ArrivalProtection protection) {
        this(settings, protection, key -> {
            var world = Bukkit.getWorld(NamespacedKey.fromString(key));
            if (world == null) throw new IllegalStateException("World unavailable: " + key);
            return world.getSpawnLocation();
        });
    }

    public ArrivalDestinations(ArrivalSettings settings, ArrivalProtection protection,
                               java.util.function.Function<String, Location> spawns) {
        this.settings = settings;
        this.protection = protection;
        this.spawns = spawns;
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
        return protectedWorld(key);
    }

    public Location protectedWorld(String key) { return protectedSpawn(spawns.apply(key), protection); }
    public Location adventureWorld(String key) { return safeSpawn(spawns.apply(key)); }

    /** Shared safe-arrival validation for public destinations. */
    public static Location protectedSpawn(String key, ArrivalProtection protection) {
        return protectedSpawn(safeSpawn(key), protection);
    }

    private static Location protectedSpawn(Location location, ArrivalProtection protection) {
        safeSpawn(location);
        for (double dx : new double[] {-0.3, 0.3})
            for (double dz : new double[] {-0.3, 0.3})
                protection.requireProtected(location.clone().add(dx, 0, dz));
        return location;
    }

    /** Adventure destinations permit combat at arrival but still require a safe standing position. */
    public static Location safeSpawn(String key) {
        var world = Bukkit.getWorld(NamespacedKey.fromString(key));
        if (world == null) {
            throw new IllegalStateException("config.yml: arrival world " + key + " is not loaded");
        }
        return safeSpawn(world.getSpawnLocation());
    }

    /** Validate an exact arrival without rounding away coordinates or camera angles. */
    public static Location safeSpawn(Location location) {
        var world=location.getWorld();
        if(world==null) throw new IllegalStateException("Warp world is unavailable");
        var key=world.getKey();
        if (!world.getWorldBorder().isInside(location)) {
            throw new IllegalStateException("Configured spawn is outside the world border: " + key);
        }
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || location.getY() < world.getMinHeight() || location.getY() + 1.8 >= world.getMaxHeight()) {
            throw new IllegalStateException("Arrival outside world height: " + key);
        }
        var body = new org.bukkit.util.BoundingBox(location.getX() - .299, location.getY() + .001,
                location.getZ() - .299, location.getX() + .299, location.getY() + 1.799, location.getZ() + .299);
        boolean supported = false;
        for (int x = (int) Math.floor(body.getMinX()); x <= (int) Math.floor(body.getMaxX()); x++) {
            for (int z = (int) Math.floor(body.getMinZ()); z <= (int) Math.floor(body.getMaxZ()); z++) {
                for (int y = (int) Math.floor(location.getY() - .05); y <= (int) Math.floor(body.getMaxY()); y++) {
                    var block = world.getBlockAt(x, y, z);
                    var type = block.getType();
                    if (block.isLiquid() || type.name().contains("FIRE") || type.name().contains("MAGMA")
                            || type.name().contains("CACTUS") || type == org.bukkit.Material.SWEET_BERRY_BUSH
                            || type == org.bukkit.Material.POWDER_SNOW || type == org.bukkit.Material.WITHER_ROSE) {
                        throw new IllegalStateException("Hazardous arrival in " + key);
                    }
                    for (var local : block.getCollisionShape().getBoundingBoxes()) {
                        var box = local.clone().shift(x, y, z);
                        if (box.overlaps(body)) throw new IllegalStateException("Obstructed arrival in " + key);
                        if (Math.abs(box.getMaxY() - location.getY()) < .06 && box.getMaxX() > body.getMinX()
                                && box.getMinX() < body.getMaxX() && box.getMaxZ() > body.getMinZ()
                                && box.getMinZ() < body.getMaxZ()) supported = true;
                    }
                }
            }
        }
        if (!supported) throw new IllegalStateException("Unsupported arrival in " + key);
        return location;
    }
}
