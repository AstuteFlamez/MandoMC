package net.mandomc.gameplay.lightsaber.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.plugin.Plugin;

/**
 * Typed wrapper for lightsaber-related values in config.yml.
 */
public class LightsaberConfig extends BaseConfig {

    public LightsaberConfig(Plugin plugin) {
        super(plugin, "config.yml");
        this.config = plugin.getConfig();
    }

    @Override
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean isAxeDisablePreventionEnabled() {
        return getBoolean("lightsabers.prevent-axe-disable", true);
    }

    public boolean isStaminaEnabled() {
        return getBoolean("lightsabers.stamina.enabled", true);
    }

    public int getDurabilityPerDeflect() {
        return Math.max(1, getInt("lightsabers.stamina.durability-per-deflect", 4));
    }

    public int getLowDurabilityThreshold() {
        return Math.max(1, getInt("lightsabers.stamina.low-durability-threshold", 8));
    }

    public int getOverheatDisableTicks() {
        return Math.max(1, getInt("lightsabers.stamina.overheat-disable-ticks", 80));
    }

    public boolean isOverheatDebuffEnabled() {
        return getBoolean("lightsabers.stamina.overheat-debuff.enabled", true);
    }

    public int getOverheatSlownessTicks() {
        return Math.max(1, getInt("lightsabers.stamina.overheat-debuff.slowness-ticks", 60));
    }

    public int getOverheatSlownessAmplifier() {
        return Math.max(0, getInt("lightsabers.stamina.overheat-debuff.slowness-amplifier", 1));
    }

    public int getOverheatWeaknessTicks() {
        return Math.max(1, getInt("lightsabers.stamina.overheat-debuff.weakness-ticks", 60));
    }

    public int getOverheatWeaknessAmplifier() {
        return Math.max(0, getInt("lightsabers.stamina.overheat-debuff.weakness-amplifier", 0));
    }

    public double getThrowRadius() {
        return Math.max(0.1, getDouble("lightsabers.throw.radius", 2.5));
    }

    public double getThrowArcHeight() {
        return Math.max(0.0, getDouble("lightsabers.throw.arc-height", 0.5));
    }

    public double getThrowMoveMultiplier() {
        double value = getDouble("lightsabers.throw.move-multiplier", 0.45);
        return Math.max(0.01, Math.min(1.0, value));
    }

    public int getThrowMaxTicks() {
        return Math.max(1, getInt("lightsabers.throw.max-ticks", 20));
    }

    public int getThrowMaxHitsBeforeReturn() {
        return Math.max(1, getInt("lightsabers.throw.max-hits-before-return", 3));
    }

    public double getThrowReturnDistance() {
        return Math.max(0.5, getDouble("lightsabers.throw.return-distance", 1.5));
    }

    public double getThrowForwardCenterDistance() {
        return Math.max(0.1, getDouble("lightsabers.throw.forward-center-distance", 2.5));
    }

    public double getThrowCollisionLookahead() {
        return Math.max(0.1, getDouble("lightsabers.throw.collision-lookahead", 0.7));
    }

    public double getThrowCollisionRayStep() {
        return Math.max(0.05, getDouble("lightsabers.throw.collision-ray-step", 0.2));
    }

    public int getThrowMaxLifetimeTicks() {
        return Math.max(10, getInt("lightsabers.throw.max-throw-lifetime-ticks", 120));
    }

    public int getThrowMaxReturnLifetimeTicks() {
        return Math.max(10, getInt("lightsabers.throw.max-return-lifetime-ticks", 120));
    }

    public int getThrowMaxSolidRecoveryTicks() {
        return Math.max(1, getInt("lightsabers.throw.max-solid-recovery-ticks", 8));
    }

    public int getThrowTrailSoundIntervalTicks() {
        return Math.max(1, getInt("lightsabers.throw.trail-sound-interval-ticks", 8));
    }

    public double getThrowEntityCollisionRange() {
        return Math.max(0.2, getDouble("lightsabers.throw.entity-collision-range", 1.2));
    }
}
