package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;

import com.ticxo.modelengine.api.model.ActiveModel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Single configurable weapon implementation driven entirely by YAML.
 * Supports bone-based multi-point firing, burst, spread, cooldown,
 * AoE damage on impact, sounds, and particles -- all from {@link WeaponConfig}.
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

        long cooldownUntil = cooldownMap.getOrDefault(uuid, 0L);
        if (now < cooldownUntil) {
            long msLeft = cooldownUntil - now;
            double secondsLeft = Math.ceil(msLeft / 100.0) / 10.0;
            shooter.sendMessage(LangManager.get(
                    "vehicles.weapon.recharging",
                    "%seconds%", String.valueOf(secondsLeft)));
            return;
        }

        String ammo = config.getAmmo();
        int boneCount = Math.max(1, config.getWeaponBones().size());
        int totalAmmo = config.getAmmoPerShot() * boneCount;

        if (ammo != null && !ammo.isBlank()) {
            if (!AmmoUtil.hasAmmo(shooter, ammo, totalAmmo)) {
                shooter.sendMessage(LangManager.get(
                        "vehicles.weapon.out-of-ammo",
                        "%ammo%", ammo.replace("_", " ")));
                return;
            }
        }

        int burst = Math.max(1, config.getBurst());
        int interval = Math.max(0, config.getBurstIntervalTicks());

        String vehicleConfigId = resolveVehicleConfigId(vehicle);

        for (int i = 0; i < burst; i++) {
            if (i == 0) {
                fireVolley(vehicle, shooter, ammo, totalAmmo, vehicleConfigId);
            } else {
                int delay = i * interval;
                Bukkit.getScheduler().runTaskLater(
                        MandoMC.getInstance(),
                        () -> fireVolley(vehicle, shooter, ammo, totalAmmo, vehicleConfigId),
                        delay
                );
            }
        }

        if (config.getCooldownMs() > 0) {
            cooldownMap.put(uuid, now + config.getCooldownMs());
        }
    }

    /**
     * Fires one projectile from each configured weapon bone simultaneously.
     * Falls back to the player eye location if no weapon bones are configured.
     */
    private void fireVolley(Vehicle vehicle, Player shooter, String ammo,
                            int totalAmmo, String vehicleConfigId) {
        if (!shooter.isOnline()) return;

        if (ammo != null && !ammo.isBlank()) {
            if (!AmmoUtil.hasAmmo(shooter, ammo, totalAmmo)) {
                shooter.sendMessage(LangManager.get(
                        "vehicles.weapon.out-of-ammo",
                        "%ammo%", ammo.replace("_", " ")));
                return;
            }
            AmmoUtil.consumeAmmo(shooter, ammo, totalAmmo);
        }

        List<String> bones = config.getWeaponBones();
        VehicleData data = vehicle.getVehicleData();
        ActiveModel model = data.getActiveModel();
        LivingEntity entity = data.getEntity();

        Set<UUID> excluded = buildExcludedSet(shooter);

        if (bones.isEmpty()) {
            Location origin = shooter.getEyeLocation();
            Vector direction = applySpread(origin.getDirection());
            ArmorStandProjectile.launch(shooter, origin, direction, config,
                    vehicleConfigId, excluded, entity);
            spawnMuzzleParticlesAt(origin.add(direction.clone().multiply(1.5)));
        } else {
            Vector direction = applySpread(entity.getLocation().getDirection());
            for (String boneName : bones) {
                Location origin = BonePositionResolver.resolve(model, entity, boneName);
                ArmorStandProjectile.launch(shooter, origin, direction, config,
                        vehicleConfigId, excluded, entity);
                spawnMuzzleParticlesAt(origin);
            }
        }

        playSound(shooter);
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

    private void spawnMuzzleParticlesAt(Location loc) {
        if (config.getParticles().isEmpty() || loc.getWorld() == null) return;

        for (WeaponConfig.ParticleEffect effect : config.getParticles()) {
            loc.getWorld().spawnParticle(
                    effect.particle(), loc,
                    effect.count(),
                    effect.spread(), effect.spread(), effect.spread(),
                    effect.speed());
        }
    }

    private static Set<UUID> buildExcludedSet(Player shooter) {
        UUID shooterUUID = shooter.getUniqueId();
        Vehicle vehicle = VehicleModule.getVehicleForPlayer(shooterUUID);
        if (vehicle == null) return Set.of(shooterUUID);

        Set<UUID> excluded = new HashSet<>(vehicle.getOccupants().keySet());
        excluded.add(shooterUUID);
        excluded.add(vehicle.getOwnerUUID());

        LivingEntity vehicleEntity = vehicle.getVehicleData().getEntity();
        if (vehicleEntity != null) {
            excluded.add(vehicleEntity.getUniqueId());
            for (org.bukkit.entity.Entity passenger : vehicleEntity.getPassengers()) {
                excluded.add(passenger.getUniqueId());
            }
        }

        return excluded;
    }

    private static String resolveVehicleConfigId(Vehicle vehicle) {
        String itemId = vehicle.getItemId();
        if (itemId == null) return "";
        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        return vehicleId != null ? vehicleId : "";
    }
}
