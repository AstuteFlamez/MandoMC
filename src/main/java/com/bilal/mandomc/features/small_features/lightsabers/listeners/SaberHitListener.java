package com.bilal.mandomc.features.small_features.lightsabers.listeners;

import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.bilal.mandomc.features.items.ItemUtils;
import com.bilal.mandomc.features.small_features.lightsabers.SaberManager;

public class SaberHitListener implements Listener {

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.SHIELD) return;

        if (!ItemUtils.hasTag(item, "SABER")) return;

        double damage = SaberManager.getMeleeDamage(item);

        event.setDamage(damage);

        player.getWorld().playSound(
                event.getEntity().getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1f
        );
    }
}