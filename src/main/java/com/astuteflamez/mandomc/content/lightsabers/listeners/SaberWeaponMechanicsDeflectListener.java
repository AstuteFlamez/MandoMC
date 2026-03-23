package com.astuteflamez.mandomc.content.lightsabers.listeners;

import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileHitEntityEvent;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.astuteflamez.mandomc.system.items.ItemUtils;

public class SaberWeaponMechanicsDeflectListener implements Listener {

    @EventHandler
    public void onProjectileHit(ProjectileHitEntityEvent event) {

        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.SHIELD)
            return;

        if (!ItemUtils.hasTag(item, "SABER"))
            return;

        if (!player.isBlocking())
            return;

        /* cancel the hit */

        event.setCancelled(true);

        WeaponProjectile projectile = event.getProjectile();

        /* reflect direction */

        Vector reflect = player.getLocation()
                .getDirection()
                .normalize()
                .multiply(3.0);

        projectile.setMotion(reflect);

        /* effects */

        player.getWorld().playSound(
                player.getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1.2f
        );

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                player.getLocation().add(0,1,0),
                12
        );

        player.getWorld().spawnParticle(
                Particle.CRIT,
                player.getLocation().add(0,1,0),
                20
        );
    }
}