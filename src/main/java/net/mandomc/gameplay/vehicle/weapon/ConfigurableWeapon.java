package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Single configurable weapon implementation driven entirely by YAML.
 * Replaces the previous per-vehicle weapon classes (XWing, TieFighter, etc.).
 * Supports single-shot, burst, spread, cooldown, custom projectiles,
 * sounds, and particles -- all from {@link WeaponConfig}.
 */
public class ConfigurableWeapon implements WeaponSystem {

    private final WeaponConfig config;
    private final Map<UUID, Long> cooldownMap = new HashMap<>();
    private final Random rng = new Random();

    public ConfigurableWeapon(WeaponConfig config) {
        this.config = config;
    }

    @Override
    public void shoot(Vehicle vehicle, Player shooter) {
        if (shooter == null || config == null) return;

        UUID uuid = shooter.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check
        long cooldownUntil = cooldownMap.getOrDefault(uuid, 0L);
        if (now < cooldownUntil) {
            long msLeft = cooldownUntil - now;
            double secondsLeft = Math.ceil(msLeft / 100.0) / 10.0;
            shooter.sendMessage(LangManager.get(
                    "vehicles.weapon.recharging",
                    "%seconds%", String.valueOf(secondsLeft)));
            return;
        }

        // Ammo check (first burst shot only; subsequent burst shots recheck)
        String ammo = config.getAmmo();
        int ammoPerShot = config.getAmmoPerShot();
        if (ammo != null && !ammo.isBlank()) {
            if (!AmmoUtil.hasAmmo(shooter, ammo, ammoPerShot)) {
                shooter.sendMessage(LangManager.get(
                        "vehicles.weapon.out-of-ammo",
                        "%ammo%", ammo.replace("_", " ")));
                return;
            }
        }

        int burst = Math.max(1, config.getBurst());
        int interval = Math.max(0, config.getBurstIntervalTicks());

        for (int i = 0; i < burst; i++) {
            if (i == 0) {
                fireSingleShot(shooter, ammo, ammoPerShot);
            } else {
                int delay = i * interval;
                Bukkit.getScheduler().runTaskLater(
                        MandoMC.getInstance(),
                        () -> fireSingleShot(shooter, ammo, ammoPerShot),
                        delay
                );
            }
        }

        if (config.getCooldownMs() > 0) {
            cooldownMap.put(uuid, now + config.getCooldownMs());
        }
    }

    private void fireSingleShot(Player shooter, String ammo, int ammoPerShot) {
        if (!shooter.isOnline()) return;

        if (ammo != null && !ammo.isBlank()) {
            if (!AmmoUtil.hasAmmo(shooter, ammo, ammoPerShot)) {
                shooter.sendMessage(LangManager.get(
                        "vehicles.weapon.out-of-ammo",
                        "%ammo%", ammo.replace("_", " ")));
                return;
            }
            AmmoUtil.consumeAmmo(shooter, ammo, ammoPerShot);
        }

        Location origin = shooter.getEyeLocation();
        Vector direction = applySpread(origin.getDirection());

        ProjectileSpawner.spawn(shooter, origin, direction, config);

        playSound(shooter);
        spawnMuzzleParticles(shooter);
    }

    private Vector applySpread(Vector base) {
        double spread = config.getSpreadDegrees();
        if (spread <= 0) return base;

        float yawOffset = (float) (rng.nextGaussian() * spread);
        float pitchOffset = (float) (rng.nextGaussian() * spread);

        Location temp = new Location(null, 0, 0, 0,
                (float) Math.toDegrees(Math.atan2(-base.getX(), base.getZ())) + yawOffset,
                (float) Math.toDegrees(-Math.asin(base.getY() / base.length())) + pitchOffset);

        return temp.getDirection();
    }

    private void playSound(Player shooter) {
        String sound = config.getSound();
        if (sound == null || sound.isBlank()) return;

        Location loc = shooter.getLocation();
        loc.getWorld().playSound(
                loc, sound, SoundCategory.MASTER,
                config.getSoundVolume(), config.getSoundPitch());
    }

    private void spawnMuzzleParticles(Player shooter) {
        if (config.getParticles().isEmpty()) return;

        Location loc = shooter.getEyeLocation().add(
                shooter.getEyeLocation().getDirection().multiply(1.5));

        for (WeaponConfig.ParticleEffect effect : config.getParticles()) {
            loc.getWorld().spawnParticle(
                    effect.particle(), loc,
                    effect.count(),
                    effect.spread(), effect.spread(), effect.spread(),
                    effect.speed());
        }
    }
}
