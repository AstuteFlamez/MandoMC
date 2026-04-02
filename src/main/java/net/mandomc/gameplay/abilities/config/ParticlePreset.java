package net.mandomc.gameplay.abilities.config;

import org.bukkit.Particle;

/**
 * Particle visual preset for an ability level.
 */
public record ParticlePreset(
        Particle type,
        int count,
        double spreadX,
        double spreadY,
        double spreadZ,
        double speed
) {
    public static ParticlePreset defaults() {
        return new ParticlePreset(Particle.CLOUD, 10, 0.2, 0.2, 0.2, 0.01);
    }
}
