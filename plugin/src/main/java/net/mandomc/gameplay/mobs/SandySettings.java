package net.mandomc.gameplay.mobs;

import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

public record SandySettings(String world, Set<String> biomes, Set<String> ground, int minLevel, int maxLevel,
        int raidersMin, int raidersMax, int banthasMax, int massiffsMax, int wildlifeMin, int wildlifeMax,
        int groupCap, int localCap, int worldCap, int radius, int interval, double chance,
        int minDistance, int maxDistance, int separation, int attempts, double rifleBase, double riflePerLevel) {
    public static SandySettings read(ConfigurationSection c) {
        if (c.getInt("schema-version") != 1) throw new IllegalArgumentException("mobs.yml: expected schema-version 1");
        var result = new SandySettings(c.getString("world", "minecraft:overworld"),
                Set.copyOf(c.getStringList("biomes")),
                c.getStringList("ground").stream().map(String::toUpperCase).collect(Collectors.toUnmodifiableSet()),
                c.getInt("levels.minimum"), c.getInt("levels.maximum"), c.getInt("patrol.raiders-min"),
                c.getInt("patrol.raiders-max"), c.getInt("patrol.banthas-max"), c.getInt("patrol.massiffs-max"),
                c.getInt("wildlife.minimum"), c.getInt("wildlife.maximum"), c.getInt("population.group-cap"),
                c.getInt("population.nearby-cap"), c.getInt("population.loaded-world-cap"),
                c.getInt("population.radius-blocks"), c.getInt("spawn.interval-seconds"),
                c.getDouble("spawn.chance"), c.getInt("spawn.minimum-player-distance"),
                c.getInt("spawn.maximum-player-distance"), c.getInt("spawn.encounter-separation"),
                c.getInt("spawn.attempts"), c.getDouble("cycler.mob-base-damage"), c.getDouble("cycler.damage-per-level"));
        if (result.world == null || result.biomes.isEmpty() || result.ground.isEmpty()
                || result.minLevel < 1 || result.maxLevel < result.minLevel || result.maxLevel > 100
                || result.raidersMin < 2 || result.raidersMax < result.raidersMin
                || result.raidersMax > result.groupCap || result.banthasMax < 0 || result.banthasMax > 2
                || result.massiffsMax < 0 || result.massiffsMax > 2
                || result.wildlifeMin < 1 || result.wildlifeMax < result.wildlifeMin
                || result.wildlifeMax > result.groupCap || result.groupCap > 12
                || result.localCap < result.groupCap || result.worldCap < result.localCap || result.worldCap > 256
                || result.radius < 32 || result.radius > 256 || result.interval < 5 || result.interval > 3600
                || !Double.isFinite(result.chance) || result.chance < 0 || result.chance > 1
                || result.minDistance < 24 || result.maxDistance <= result.minDistance || result.maxDistance > 128
                || result.separation < 16 || result.separation > result.radius
                || result.attempts < 1 || result.attempts > 32
                || !Double.isFinite(result.rifleBase) || result.rifleBase <= 0 || result.rifleBase > 100
                || !Double.isFinite(result.riflePerLevel) || result.riflePerLevel < 0 || result.riflePerLevel > 10) {
            throw new IllegalArgumentException("mobs.yml: invalid levels, group budget, population, spawn or cycler bounds");
        }
        return result;
    }

    public double rifleDamage(int level) {
        return rifleBase + (Math.clamp(level, minLevel, maxLevel) - 1) * riflePerLevel;
    }
}
