package com.astuteflamez.mandomc.content.lightsabers.listeners;

import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.content.lightsabers.SaberManager;
import com.astuteflamez.mandomc.system.items.ItemUtils;

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