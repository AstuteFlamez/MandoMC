package com.bilal.mandomc.features.vehicles.weapons;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.items.configs.ItemsConfig;
import com.bilal.mandomc.features.vehicles.Vehicle;
import com.bilal.mandomc.features.vehicles.utils.AmmoUtil;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
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

        ConfigurationSection vehicleSection = ItemsConfig.getItemSection("speederbike");
        if (vehicleSection == null) return;

        ConfigurationSection weaponSection =
                vehicleSection.getConfigurationSection("systems.weapon");

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
            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cWeapon recharging: §e" + secondsLeft + "s");
            return;
        }

        if (!AmmoUtil.hasAmmo(player, ammo, ammoPerShot)) {
            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cOut of " + (ammo != null ? ammo.replace("_", " ") : "ammo") + "!");
            return;
        }

        // Safety check to ensure the vehicle still exists in the active map
        if (!MandoMC.activeVehicles.containsKey(uuid)) return;

        var activeModel = MandoMC.activeVehicles.get(uuid)
                .getVehicleData()
                .getActiveModel();

        // FIX: Use .map() and .orElse() to prevent NoSuchElementException if bones are missing
        Location leftBarrel = activeModel.getBone("barrelLeft")
                .map(bone -> bone.getLocation())
                .orElse(player.getEyeLocation());

        Location rightBarrel = activeModel.getBone("barrelRight")
                .map(bone -> bone.getLocation())
                .orElse(player.getEyeLocation());

        // Consume ammo only after we are sure we can fire
        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);

        Vector direction = player.getLocation().getDirection();

        for (int i = 0; i < ammoPerShot; i++) {
            // Alternate between left and right barrels
            Location shootLoc = (i % 2 == 0) ? leftBarrel.clone() : rightBarrel.clone();

            // Ensure projectile travels the direction player is looking
            shootLoc.setDirection(direction);

            // Using shootLoc.getDirection() ensures it fires where the bone is pointing (aligned with player)
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