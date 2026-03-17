package com.bilal.mandomc.features.small_features.teleportation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.bilal.mandomc.MandoMC;

import java.util.*;

public class TeleportWarmupListener implements Listener {

    private final Map<UUID, BukkitRunnable> warmups = new HashMap<>();
    private final Set<UUID> allowedTeleports = new HashSet<>();

    private static final int WARMUP_SECONDS = 3;

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Bypass system entirely if player is on Ilum
        if (player.getWorld().getName().equalsIgnoreCase("Ilum")) {
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) return;

        // allow teleport if it's from our system
        if (allowedTeleports.remove(uuid)) return;

        event.setCancelled(true);

        Location destination = event.getTo();

        player.sendTitle("§6Teleport Charging", "§7Don't move...", 5, 40, 5);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);

        BukkitRunnable task = new BukkitRunnable() {

            int time = WARMUP_SECONDS;

            @Override
            public void run() {

                if (!player.isOnline()) {
                    cancelWarmup(uuid);
                    return;
                }

                if (time <= 0) {

                    allowedTeleports.add(uuid);
                    player.teleport(destination);

                    player.spawnParticle(
                            Particle.PORTAL,
                            player.getLocation(),
                            80,
                            1,
                            1,
                            1,
                            0.3
                    );

                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    player.sendTitle("§aTeleported!", "", 5, 20, 5);

                    cancelWarmup(uuid);
                    return;
                }

                player.sendTitle(
                        "§eTeleporting...",
                        "§6" + time + "...",
                        0,
                        20,
                        0
                );

                player.spawnParticle(
                        Particle.PORTAL,
                        player.getLocation(),
                        20,
                        0.5,
                        0.5,
                        0.5,
                        0.05
                );

                time--;
            }

        };

        task.runTaskTimer(MandoMC.getInstance(), 0, 20);
        warmups.put(uuid, task);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!warmups.containsKey(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // 🔥 IGNORE HEAD MOVEMENT (yaw/pitch only)
        if (from.getX() == to.getX() &&
            from.getY() == to.getY() &&
            from.getZ() == to.getZ()) {
            return;
        }

        // 🔥 ALLOW SMALL MOVEMENT (tolerance)
        double distance = from.distanceSquared(to);

        // threshold ≈ 0.2 blocks movement
        if (distance < 0.04) return;

        // ❌ real movement → cancel
        player.sendTitle("§cTeleport Cancelled", "§7You moved!", 5, 30, 5);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);

        cancelWarmup(uuid);
    }

    private void cancelWarmup(UUID uuid) {

        BukkitRunnable task = warmups.remove(uuid);

        if (task != null) {
            task.cancel();
        }
    }
}