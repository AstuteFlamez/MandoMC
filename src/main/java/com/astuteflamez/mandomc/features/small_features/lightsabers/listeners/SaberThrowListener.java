package com.astuteflamez.mandomc.features.small_features.lightsabers.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;

import java.util.*;

public class SaberThrowListener implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    @EventHandler
    public void throwLightsaber(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.SHIELD) return;
        if (!ItemUtils.hasTag(item, "SABER")) return;

        if (!(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK))
            return;

        if (!player.isSneaking()) return;

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return;

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);
        if (section == null) return;

        ConfigurationSection stats = section.getConfigurationSection("stats");
        if (stats == null) return;

        double damage = stats.getDouble("throw_damage", 5);
        int cooldownSeconds = stats.getInt("throw_cooldown", 2);

        long cooldownMillis = cooldownSeconds * 1000L;

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < cooldownMillis) {

            long secondsLeft = (cooldownMillis - (now - last)) / 1000;
            player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §6Saber cooldown: §c" + secondsLeft + "§6s");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        launchSaber(player, item.clone(), damage);
    }

    private void launchSaber(Player player, ItemStack saber, double damage) {

        ItemStack handItem = player.getInventory().getItemInMainHand();
        handItem.setAmount(handItem.getAmount() - 1);

        ArmorStand stand = player.getWorld().spawn(player.getLocation().add(0,0.6,0), ArmorStand.class, s -> {

            s.setInvisible(true);
            s.setSmall(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setArms(true);

            s.getEquipment().setItemInMainHand(saber);

            s.setRightArmPose(new EulerAngle(Math.toRadians(40),0,Math.toRadians(110)));
        });

        Vector forward = player.getLocation().getDirection().normalize();
        Vector side = forward.clone().crossProduct(new Vector(0,1,0)).normalize();

        double radius = 2.5;
        int maxTicks = 20;

        Set<UUID> hitEntities = new HashSet<>();

        new BukkitRunnable() {

            int ticks = 0;
            boolean returning = false;
            int hits = 0;

            Vector arcCenter = player.getLocation().add(forward.clone().multiply(radius)).toVector();
            double arcRadius = radius;

            @Override
            public void run() {

                if (stand.isDead()) {
                    cancel();
                    return;
                }

                /* Update arc center while returning so saber tracks moving player */

                if (returning) {
                    arcCenter = player.getLocation().add(forward.clone().multiply(radius)).toVector();
                }

                double progress = ticks / (double) maxTicks;
                double angle = progress * Math.PI;

                Vector offset =
                        side.clone().multiply(Math.cos(angle) * arcRadius)
                        .add(forward.clone().multiply(Math.sin(angle) * arcRadius));

                offset.setY(Math.sin(angle) * 0.5);

                Vector target = arcCenter.clone().add(offset);
                Vector move = target.subtract(stand.getLocation().toVector()).multiply(0.45);

                Location next = stand.getLocation().clone().add(move);

                /* BLOCK COLLISION */

                if (next.getBlock().getType().isSolid()) {

                    stand.getWorld().playSound(
                            next,
                            "melee.lightsaber.hit",
                            SoundCategory.PLAYERS,
                            1f,
                            0.9f
                    );

                    stand.getWorld().spawnParticle(Particle.SWEEP_ATTACK, next, 10);
                    stand.getWorld().spawnParticle(Particle.CRIT, next, 18);
                    stand.getWorld().spawnParticle(
                            Particle.BLOCK,
                            next,
                            20,
                            0.2,0.2,0.2,
                            next.getBlock().getBlockData()
                    );

                    returning = true;
                    side.multiply(-1);

                    arcCenter.add(new Vector(
                            (random.nextDouble()-0.5)*0.5,
                            0,
                            (random.nextDouble()-0.5)*0.5
                    ));

                    move.multiply(1.2);
                }

                stand.teleport(next);

                /* SPIN + LEAN */

                EulerAngle rot = stand.getRightArmPose();
                double lean = 100 + Math.sin(ticks * 0.3) * 30;

                stand.setRightArmPose(
                        new EulerAngle(
                                rot.getX() + Math.toRadians(45),
                                0,
                                Math.toRadians(lean)
                        )
                );

                if (ticks % 3 == 0) {
                    stand.getWorld().spawnParticle(Particle.CRIT, stand.getLocation(), 1);
                }

                if (ticks % 8 == 0) {
                    stand.getWorld().playSound(
                            stand.getLocation(),
                            "melee.lightsaber.throw",
                            SoundCategory.PLAYERS,
                            0.9f,
                            1f
                    );
                }

                /* ENTITY COLLISION */

                for (Entity entity : stand.getNearbyEntities(1.2,1.2,1.2)) {

                    if (!(entity instanceof LivingEntity living)) continue;
                    if (entity == player) continue;
                    if (hitEntities.contains(entity.getUniqueId())) continue;

                    hitEntities.add(entity.getUniqueId());

                    living.damage(damage, player);

                    living.setVelocity(
                            stand.getLocation().toVector()
                                    .subtract(living.getLocation().toVector())
                                    .normalize()
                                    .multiply(-0.4)
                    );

                    stand.getWorld().playSound(
                            stand.getLocation(),
                            "melee.lightsaber.hit",
                            SoundCategory.PLAYERS,
                            1f,
                            1f
                    );

                    living.getWorld().spawnParticle(
                            Particle.SWEEP_ATTACK,
                            living.getLocation().add(0,1,0),
                            12
                    );

                    living.getWorld().spawnParticle(
                            Particle.CRIT,
                            living.getLocation().add(0,1,0),
                            18
                    );

                    hits++;

                    if (!returning) {

                        returning = true;
                        side.multiply(-1);

                        arcCenter.add(new Vector(
                                (random.nextDouble()-0.5)*0.5,
                                0,
                                (random.nextDouble()-0.5)*0.5
                        ));

                        move.multiply(1.25);
                    }
                }

                if (hits >= 3)
                    returning = true;

                if (returning && stand.getLocation().distance(player.getLocation()) < 1.5) {

                    returnSaber(player, saber, stand);
                    cancel();
                }

                ticks++;

                if (ticks > maxTicks)
                    returning = true;
            }

        }.runTaskTimer(MandoMC.getInstance(), 0L, 1L);
    }

    private void returnSaber(Player player, ItemStack saber, ArmorStand stand) {

        if (!stand.isDead())
            stand.remove();

        if (player.getInventory().firstEmpty() != -1) {

            player.getInventory().addItem(saber);

        } else {

            player.getWorld().dropItemNaturally(player.getLocation(), saber);
        }

        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0,1,0),
                10,
                0.3,0.3,0.3,
                0.05
        );
    }
}