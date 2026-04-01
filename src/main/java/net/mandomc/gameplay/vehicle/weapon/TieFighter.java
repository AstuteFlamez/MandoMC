package net.mandomc.gameplay.vehicle.weapon;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;
import net.mandomc.core.LangManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

public class TieFighter implements WeaponSystem {

    private final Random rng = new Random();

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

        debug("TieFighter shoot requested by " + shooter.getName() + " vehicle=" + vehicleId);
        fireBurst(vehicle, shooter, weaponSection);
    }

    private void fireBurst(Vehicle vehicle, Player shooter, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);

        int burst = config.getInt("burst", 1);
        int burstInterval = config.getInt("burst_interval", 0);
        double spread = config.getDouble("spread", 0);

        String sound = config.getString("sound");

        debug("TieFighter fire config gun=" + gun + " ammo=" + ammo + " burst=" + burst + " ammo_per_shot=" + ammoPerShot);

        if (!isValidWeaponTitle(gun)) {
            debug("TieFighter fire blocked: invalid WeaponMechanics gun '" + gun + "'");
            return;
        }

        for (int i = 0; i < burst; i++) {

            Bukkit.getScheduler().runTaskLater(
                    MandoMC.getInstance(),
                    () -> {

                        if (!AmmoUtil.hasAmmo(shooter, ammo, ammoPerShot)) {
                            debug("TieFighter burst blocked: missing ammo " + ammo);
                            shooter.sendMessage(LangManager.get("vehicles.weapon.out-of-ammo", "%ammo%", ammo.replace("_", " ")));
                            return;
                        }

                        AmmoUtil.consumeAmmo(shooter, ammo, ammoPerShot);
                        for (int shot = 0; shot < ammoPerShot; shot++) {
                            Vector shotDirection = directionFromShooterYawPitch(shooter, spread);
                            debug("TieFighter shoot invoke gun=" + gun
                                    + " dir=" + formatVector(shotDirection));
                            try {
                                WeaponMechanicsAPI.shoot(shooter, gun, shotDirection);
                            } catch (IllegalArgumentException ex) {
                                debug("TieFighter shoot failed for gun='" + gun + "' reason=" + ex.getMessage());
                            }
                        }

                        if (sound != null) {
                            Location soundLoc = shooter.getLocation();
                            soundLoc.getWorld().playSound(
                                    soundLoc,
                                    sound,
                                    SoundCategory.MASTER,
                                    1f,
                                    1f
                            );
                        }

                    },
                    i * burstInterval
            );
        }
    }

    private static void debug(String message) {
        MandoMC.getInstance().getLogger().info("[VehicleDebug] " + message);
    }

    private static String formatVector(Vector vector) {
        return String.format("(%.3f, %.3f, %.3f)", vector.getX(), vector.getY(), vector.getZ());
    }

    private Vector directionFromShooterYawPitch(Player shooter, double spreadDegrees) {
        Location eye = shooter.getEyeLocation();
        float yaw = eye.getYaw();
        float pitch = eye.getPitch();

        if (spreadDegrees > 0) {
            yaw += (float) (rng.nextGaussian() * spreadDegrees);
            pitch += (float) (rng.nextGaussian() * spreadDegrees);
        }

        return new Location(shooter.getWorld(), 0, 0, 0, yaw, pitch).getDirection();
    }

    private static boolean isValidWeaponTitle(String gun) {
        if (gun == null || gun.isBlank()) return false;
        ItemStack generated = WeaponMechanicsAPI.generateWeapon(gun);
        if (generated == null) return false;
        String resolved = WeaponMechanicsAPI.getWeaponTitle(generated);
        return gun.equalsIgnoreCase(resolved);
    }

}