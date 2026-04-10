package net.mandomc.gameplay.vehicle.rotation;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Pitch and roll angle limits for aerial vehicles, parsed from YAML.
 * Pitch is clamped so the vehicle cannot fly straight up/down even if
 * the player looks beyond the limit. Roll is the A/D banking cap.
 */
public record RotationLimits(float maxPitch, float maxRoll) {

    public static final RotationLimits DEFAULT = new RotationLimits(30f, 15f);

    public static RotationLimits fromConfig(ConfigurationSection section) {
        if (section == null) return DEFAULT;
        float maxPitch = (float) section.getDouble("max_pitch", DEFAULT.maxPitch);
        float maxRoll  = (float) section.getDouble("max_roll", DEFAULT.maxRoll);
        return new RotationLimits(maxPitch, maxRoll);
    }
}
