package com.bilal.mandomc.features.small_features.lightsabers.listeners;

import com.bilal.mandomc.features.items.ItemUtils;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class SaberDeflectListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {

        Projectile projectile = event.getEntity();

        if (!(event.getHitEntity() instanceof Player player))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.SHIELD)
            return;

        if (!ItemUtils.hasTag(item, "SABER"))
            return;

        if (!player.isBlocking())
            return;

        /* Deflect projectile */

        Vector reflectDirection = player.getLocation()
                .getDirection()
                .normalize()
                .multiply(1.6);

        projectile.setVelocity(reflectDirection);

        projectile.setShooter(player);

        /* Effects */

        player.getWorld().playSound(
                player.getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1.2f
        );

        player.getWorld().spawnParticle(
                Particle.CRIT,
                player.getLocation().add(0,1,0),
                15,
                0.2,0.2,0.2
        );

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                player.getLocation().add(0,1,0),
                8
        );
    }
}