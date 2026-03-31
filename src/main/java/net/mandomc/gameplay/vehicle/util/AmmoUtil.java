package net.mandomc.gameplay.vehicle.util;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AmmoUtil {

    /**
     * Checks if the player has enough ammo of the given type.
     */
    public static boolean hasAmmo(Player player, String ammoTitle, int amount) {

        ItemStack ammo = WeaponMechanicsAPI.generateAmmo(ammoTitle, false);

        if (ammo == null || ammo.getType() == Material.AIR) return false;

        int found = 0;

        for (ItemStack item : player.getInventory().getContents()) {

            if (item == null) continue;

            if (item.isSimilar(ammo)) {
                found += item.getAmount();
            }

            if (found >= amount) return true;
        }

        return false;
    }

    /**
     * Removes ammo from the player's inventory.
     */
    public static void consumeAmmo(Player player, String ammoTitle, int amount) {

        ItemStack ammo = WeaponMechanicsAPI.generateAmmo(ammoTitle, false);

        if (ammo == null || ammo.getType() == Material.AIR) return;

        ammo.setAmount(amount);

        player.getInventory().removeItem(ammo);
    }
}