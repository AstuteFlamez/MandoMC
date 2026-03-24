package net.mandomc.system.vehicles.weapons;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.system.items.configs.ItemsConfig;
import net.mandomc.system.vehicles.Vehicle;
import net.mandomc.system.vehicles.utils.AmmoUtil;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XWing implements WeaponSystem {

    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    @Override
    public void shoot(Vehicle vehicle) {

        Player player = Bukkit.getPlayer(vehicle.getOwnerUUID());
        if (player == null) return;

        ConfigurationSection vehicleSection = ItemsConfig.getItemSection("xwing");
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

            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cOut of " + ammo.replace("_", " ") + "!");
            return;
        }

        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);

        Vector direction = player.getLocation().getDirection();

        WeaponMechanicsAPI.shoot(player, gun, direction);

        if (sound != null) {

            player.getWorld().playSound(
                    player.getLocation(),
                    sound,
                    SoundCategory.MASTER,
                    1.0f,
                    1.0f
            );
        }

        if (cooldown > 0) {
            cooldownMap.put(uuid, now + cooldown);
        }
    }
}