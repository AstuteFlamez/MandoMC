package com.astuteflamez.mandomc.system.vehicles.weapons;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;
import com.astuteflamez.mandomc.system.vehicles.utils.AmmoUtil;

import java.util.Random;

public class TieFighter implements WeaponSystem {

    private final Random rng = new Random();

    @Override
    public void shoot(Vehicle vehicle) {

        Player player = Bukkit.getPlayer(vehicle.getOwnerUUID());
        if (player == null) return;

        ConfigurationSection vehicleSection = ItemsConfig.getItemSection("tiefighter");
        if (vehicleSection == null) return;

        ConfigurationSection weaponSection =
                vehicleSection.getConfigurationSection("systems.weapon");

        if (weaponSection == null) return;

        fireBurst(player, weaponSection);
    }

    private void fireBurst(Player player, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);

        int burst = config.getInt("burst", 1);
        int burstInterval = config.getInt("burst_interval", 0);
        double spread = config.getDouble("spread", 0);

        String sound = config.getString("sound");

        for (int i = 0; i < burst; i++) {

            Bukkit.getScheduler().runTaskLater(
                    MandoMC.getInstance(),
                    () -> {

                        if (!AmmoUtil.hasAmmo(player, ammo, ammoPerShot)) {
                            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cOut of " + ammo.replace("_", " ") + "!");
                            return;
                        }

                        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);

                        Vector dir = player.getLocation().getDirection().normalize();
                        Vector spreadDir = applySpread(dir, spread);

                        WeaponMechanicsAPI.shoot(player, gun, spreadDir);

                        if (sound != null) {
                            player.getWorld().playSound(
                                    player.getLocation(),
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

    private Vector applySpread(Vector dir, double degStd) {

        if (degStd <= 0) return dir;

        Vector w = dir.clone().normalize();
        Vector temp = Math.abs(w.getY()) < 0.999 ? new Vector(0,1,0) : new Vector(1,0,0);

        Vector u = w.clone().getCrossProduct(temp).normalize();
        Vector v = w.clone().getCrossProduct(u).normalize();

        double radStd = Math.toRadians(degStd);

        double offsetU = rng.nextGaussian() * Math.tan(radStd);
        double offsetV = rng.nextGaussian() * Math.tan(radStd);

        return w.clone()
                .add(u.multiply(offsetU))
                .add(v.multiply(offsetV))
                .normalize();
    }
}