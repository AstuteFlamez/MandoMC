package net.mandomc.gameplay.vehicle.weapon;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;
import net.mandomc.core.LangManager;
import net.mandomc.MandoMC;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XWing implements WeaponSystem {

    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    @Override
    public void shoot(Vehicle vehicle, Player shooter) {
        if (shooter == null) return;

        String vehicleId = VehicleRegistry.getVehicleId(vehicle.getItemId());
        if (vehicleId == null) return;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return;

        ConfigurationSection weaponSection =
                config.getConfigurationSection("vehicle.systems.weapon");

        if (weaponSection == null) return;

        debug("XWing shoot requested by " + shooter.getName() + " vehicle=" + vehicleId);
        fireWeapon(vehicle, shooter, weaponSection);
    }

    private void fireWeapon(Vehicle vehicle, Player shooter, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);
        long cooldown = config.getLong("cooldown", 0);
        String sound = config.getString("sound");

        debug("XWing fire config gun=" + gun + " ammo=" + ammo + " ammo_per_shot=" + ammoPerShot);

        if (!isValidWeaponTitle(gun)) {
            debug("XWing fire blocked: invalid WeaponMechanics gun '" + gun + "'");
            return;
        }

        UUID uuid = shooter.getUniqueId();
        long now = System.currentTimeMillis();

        long cooldownUntil = cooldownMap.getOrDefault(uuid, 0L);

        if (now < cooldownUntil) {
            long msLeft = cooldownUntil - now;
            double secondsLeft = Math.ceil(msLeft / 100.0) / 10.0;
            debug("XWing fire blocked by cooldown: " + secondsLeft + "s left");
            shooter.sendMessage(LangManager.get("vehicles.weapon.recharging", "%seconds%", String.valueOf(secondsLeft)));
            return;
        }

        if (!AmmoUtil.hasAmmo(shooter, ammo, ammoPerShot)) {
            debug("XWing fire blocked: missing ammo " + ammo);
            shooter.sendMessage(LangManager.get("vehicles.weapon.out-of-ammo", "%ammo%", ammo.replace("_", " ")));
            return;
        }

        AmmoUtil.consumeAmmo(shooter, ammo, ammoPerShot);
        for (int i = 0; i < ammoPerShot; i++) {
            Vector shotDirection = shooter.getEyeLocation().getDirection();
            debug("XWing shoot invoke gun=" + gun
                    + " dir=" + formatVector(shotDirection));
            try {
                WeaponMechanicsAPI.shoot(shooter, gun, shotDirection);
            } catch (IllegalArgumentException ex) {
                debug("XWing shoot failed for gun='" + gun + "' reason=" + ex.getMessage());
            }
        }

        if (sound != null) {
            var soundLoc = shooter.getLocation();
            soundLoc.getWorld().playSound(soundLoc, sound, SoundCategory.MASTER, 1f, 1f);
        }

        if (cooldown > 0) {
            cooldownMap.put(uuid, now + cooldown);
        }
    }

    private static void debug(String message) {
        MandoMC.getInstance().getLogger().info("[VehicleDebug] " + message);
    }

    private static String formatVector(Vector vector) {
        return String.format("(%.3f, %.3f, %.3f)", vector.getX(), vector.getY(), vector.getZ());
    }

    private static boolean isValidWeaponTitle(String gun) {
        if (gun == null || gun.isBlank()) return false;
        ItemStack generated = WeaponMechanicsAPI.generateWeapon(gun);
        if (generated == null) return false;
        String resolved = WeaponMechanicsAPI.getWeaponTitle(generated);
        return gun.equalsIgnoreCase(resolved);
    }
}