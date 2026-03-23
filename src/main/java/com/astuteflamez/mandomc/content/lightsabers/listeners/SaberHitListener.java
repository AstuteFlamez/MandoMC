package com.astuteflamez.mandomc.content.lightsabers.listeners;

import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.content.lightsabers.SaberManager;
import com.astuteflamez.mandomc.system.items.ItemUtils;

/**
 * Handles melee attacks performed with lightsabers.
 *
 * Overrides default damage values with configured saber stats
 * and plays hit effects.
 */
public class SaberHitListener implements Listener {

    /**
     * Handles entity damage events caused by players.
     *
     * Applies lightsaber melee damage if the player is holding
     * a valid saber.
     *
     * @param event the entity damage event
     */
    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isValidSaber(item)) return;

        double damage = SaberManager.getMeleeDamage(item);
        event.setDamage(damage);

        playHitEffect(player, event);
    }

    /**
     * Validates whether the given item is a lightsaber.
     *
     * @param item the item to check
     * @return true if the item is a valid saber
     */
    private boolean isValidSaber(ItemStack item) {
        return item != null
                && item.getType() == Material.SHIELD
                && ItemUtils.hasTag(item, "SABER");
    }

    /**
     * Plays hit sound effects for a successful saber strike.
     *
     * @param player the attacking player
     * @param event the damage event
     */
    private void playHitEffect(Player player, EntityDamageByEntityEvent event) {

        player.getWorld().playSound(
                event.getEntity().getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1f
        );
    }
}