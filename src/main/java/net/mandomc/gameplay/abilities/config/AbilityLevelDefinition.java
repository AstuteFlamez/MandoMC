package net.mandomc.gameplay.abilities.config;

/**
 * Tunable level data for a specific ability level.
 */
public record AbilityLevelDefinition(
        int level,
        int tokenCost,
        double cooldownSeconds,
        double jumpMultiplier,
        double pushPower,
        double pushRange,
        double pushRadius,
        ParticlePreset particle
) {
}
