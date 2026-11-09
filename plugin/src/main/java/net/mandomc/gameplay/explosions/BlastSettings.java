package net.mandomc.gameplay.explosions;

import org.bukkit.configuration.ConfigurationSection;

public record BlastSettings(int radius, int maxBlocks, int restoreSeconds, int pendingLimit, int restoreBlocksPerStep, int restoreIntervalTicks) {
    public BlastSettings(int radius, int maxBlocks, int restoreSeconds, int pendingLimit) {
        this(radius, maxBlocks, restoreSeconds, pendingLimit, 4, 2);
    }

    public BlastSettings {
        if (radius < 1
                || radius > 16
                || maxBlocks < 1
                || maxBlocks > 4096
                || restoreSeconds < 1
                || restoreSeconds > 3600
                || pendingLimit < maxBlocks
                || pendingLimit > 65536
                || restoreBlocksPerStep < 1 || restoreBlocksPerStep > 64
                || restoreIntervalTicks < 1 || restoreIntervalTicks > 20) {
            throw new IllegalArgumentException(
                    "worlds/explosions.yml: invalid radius, block cap, delay or pending limit");
        }
    }

    public static BlastSettings read(ConfigurationSection c) {
        if (c.getInt("schema-version") != 1)
            throw new IllegalArgumentException("worlds/explosions.yml: expected schema 1");
        return new BlastSettings(
                c.getInt("radius"),
                c.getInt("max-blocks"),
                c.getInt("restore-seconds"),
                c.getInt("pending-limit"),
                c.getInt("restore-blocks-per-step", 4),
                c.getInt("restore-interval-ticks", 2));
    }
}
