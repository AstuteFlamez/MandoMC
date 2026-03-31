package net.mandomc.gameplay.vehicle.weapon;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpeederBike implements WeaponSystem {

    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    @Override
    public void shoot(Vehicle vehicle) {

        Player player = Bukkit.getPlayer(vehicle.getOwnerUUID());
        if (player == null) return;

        String vehicleId = VehicleRegistry.getVehicleId(vehicle.getItemId());
        if (vehicleId == null) return;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return;

        ConfigurationSection weaponSection =
                config.getConfigurationSection("vehicle.systems.weapon");

        if (weaponSection == null) return;

        fireWeapon(player, weaponSection);
    }

    private void fireWeapon(Player player, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);
        long cooldown = config.getLong("cooldown", 0);
        String sound = config.getString("sound");

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        long cooldownUntil = cooldownMap.getOrDefault(uuid, 0L);

        if (now < cooldownUntil) {
            long msLeft = cooldownUntil - now;
            double secondsLeft = Math.ceil(msLeft / 100.0) / 10.0;
            player.sendMessage(LangManager.get("vehicles.weapon.recharging", "%seconds%", String.valueOf(secondsLeft)));
            return;
        }

        if (!AmmoUtil.hasAmmo(player, ammo, ammoPerShot)) {
            player.sendMessage(LangManager.get("vehicles.weapon.out-of-ammo", "%ammo%", (ammo != null ? ammo.replace("_", " ") : "ammo")));
            return;
        }

        if (!VehicleModule.getActiveVehicles().containsKey(uuid)) return;

        var activeModel = VehicleModule.getActiveVehicles().get(uuid)
                .getVehicleData()
                .getActiveModel();

        Location leftBarrel = activeModel.getBone("barrelLeft")
                .map(bone -> bone.getLocation())
                .orElse(player.getEyeLocation());

        Location rightBarrel = activeModel.getBone("barrelRight")
                .map(bone -> bone.getLocation())
                .orElse(player.getEyeLocation());

        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);

        Vector direction = player.getLocation().getDirection();

        for (int i = 0; i < ammoPerShot; i++) {

            Location shootLoc = (i % 2 == 0) ? leftBarrel.clone() : rightBarrel.clone();
            shootLoc.setDirection(direction);

            WeaponMechanicsAPI.shoot(player, gun, shootLoc.getDirection());
        }

        if (sound != null) {
            player.getWorld().playSound(
                    player.getLocation(),
                    sound,
                    SoundCategory.MASTER,
                    1f,
                    1f
            );
        }

        if (cooldown > 0) {
            cooldownMap.put(uuid, now + cooldown);
        }
    }
}