package net.mandomc.gameplay.travel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Stable dimension keys, exact doubles and facing; no persisted Bukkit world objects. */
public record SavedLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public SavedLocation {
        if (NamespacedKey.fromString(world) == null || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || Math.abs(pitch) > 90) {
            throw new IllegalArgumentException("Invalid saved location");
        }
    }
    public static SavedLocation of(Location location) {
        return new SavedLocation(location.getWorld().getKey().toString(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }
    public String encode() { return world + ";" + x + ";" + y + ";" + z + ";" + yaw + ";" + pitch; }
    public static SavedLocation decode(String data) {
        var fields = data.split(";", -1);
        if (fields.length != 6) throw new IllegalArgumentException("Invalid saved location format");
        return new SavedLocation(fields[0], Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                Double.parseDouble(fields[3]), Float.parseFloat(fields[4]), Float.parseFloat(fields[5]));
    }
    public Location resolve() {
        var loaded = Bukkit.getWorld(NamespacedKey.fromString(world));
        if (loaded == null) throw new IllegalStateException("Saved world is not loaded: " + world);
        if (y < loaded.getMinHeight() || y + 1 >= loaded.getMaxHeight()) {
            throw new IllegalStateException("Saved location is outside world height");
        }
        return new Location(loaded, x, y, z, yaw, pitch);
    }
}
