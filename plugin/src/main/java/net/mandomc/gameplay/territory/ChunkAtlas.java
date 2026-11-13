package net.mandomc.gameplay.territory;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** A bounded, immutable chunk projection. This is presentation data, not protection or ownership. */
public record ChunkAtlas(String world, String name, int minX, int minZ, int maxX, int maxZ, List<String> rows) {
    public ChunkAtlas {
        rows = List.copyOf(rows);
        if (world == null || world.isBlank() || name == null || name.isBlank()
                || minX > maxX || minZ > maxZ || (long) maxX - minX >= 64 || (long) maxZ - minZ >= 64) {
            throw new IllegalArgumentException("territory/maps.yml: invalid world, name or bounds (maximum 64 chunks)");
        }
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        if (rows.size() != height || rows.stream().anyMatch(row -> row.length() != width || !row.matches("[SW.]+"))) {
            throw new IllegalArgumentException("territory/maps.yml: rows must exactly cover bounds using S, W or .");
        }
    }

    public static ChunkAtlas read(ConfigurationSection config) {
        if (config == null || config.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("territory/maps.yml: expected schema-version 1");
        }
        return new ChunkAtlas(config.getString("world"), config.getString("name"),
                requiredInt(config, "min-chunk-x"), requiredInt(config, "min-chunk-z"),
                requiredInt(config, "max-chunk-x"), requiredInt(config, "max-chunk-z"), config.getStringList("rows"));
    }

    private static int requiredInt(ConfigurationSection config, String key) {
        if (!config.isInt(key)) throw new IllegalArgumentException("territory/maps.yml: missing integer " + key);
        return config.getInt(key);
    }

    public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }
    public char zone(int x, int z) { return contains(x, z) ? rows.get(z - minZ).charAt(x - minX) : '.'; }
}
