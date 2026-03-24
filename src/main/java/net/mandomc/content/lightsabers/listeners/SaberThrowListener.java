package net.mandomc.content.lightsabers.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import net.mandomc.MandoMC;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.config.ItemsConfig;

import java.util.*;

/**
 * Handles lightsaber throwing ability.
 *
 * Allows players to throw their saber, damaging entities along
 * a curved path and returning it after completion.
 */
public class SaberThrowListener implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    /**
     * Handles player interaction for saber throwing.
     *
     * Validates conditions, checks cooldown, and launches the saber.
     *
     * @param event the interact event
     */
    @EventHandler
    public void throwLightsaber(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isValidThrow(player, item, event.getAction())) return;

        String itemId = ItemUtils.getItemId(item);
        ConfigurationSection stats = getStats(itemId);
        if (stats == null) return;

        double damage = stats.getDouble("throw_damage", 5);
        int cooldownSeconds = stats.getInt("throw_cooldown", 2);

        if (isOnCooldown(player, cooldownSeconds)) {
            sendCooldownMessage(player, cooldownSeconds);
            return;
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        launchSaber(player, item.clone(), damage);
    }

    /**
     * Validates whether the player can throw the saber.
     */
    private boolean isValidThrow(Player player, ItemStack item, Action action) {
        return item != null
                && item.getType() == Material.SHIELD
                && ItemUtils.hasTag(item, "SABER")
                && player.isSneaking()
                && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }

    /**
     * Retrieves stats section for an item.
     */
    private ConfigurationSection getStats(String itemId) {
        if (itemId == null) return null;

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);
        return section != null ? section.getConfigurationSection("stats") : null;
    }

    /**
     * Checks if player is on cooldown.
     */
    private boolean isOnCooldown(Player player, int cooldownSeconds) {

        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        return now - last < cooldownMillis;
    }

    /**
     * Sends cooldown message to player.
     */
    private void sendCooldownMessage(Player player, int cooldownSeconds) {

        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = (cooldownSeconds * 1000L - (now - last)) / 1000;

        player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §6Saber cooldown: §c" + remaining + "§6s");
    }

    /**
     * Launches the saber as a moving armor stand projectile.
     */
    private void launchSaber(Player player, ItemStack saber, double damage) {

        removeOneFromHand(player);

        ArmorStand stand = spawnSaberStand(player, saber);

        Vector forward = player.getLocation().getDirection().normalize();
        Vector side = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        Set<UUID> hitEntities = new HashSet<>();

        new BukkitRunnable() {

            int ticks = 0;
            boolean returning = false;
            int hits = 0;

            final double radius = 2.5;
            final int maxTicks = 20;

            Vector arcCenter = player.getLocation().add(forward.clone().multiply(radius)).toVector();

            @Override
            public void run() {

                if (stand.isDead()) {
                    cancel();
                    return;
                }

                if (returning) {
                    arcCenter = player.getLocation().add(forward.clone().multiply(radius)).toVector();
                }

                Location next = calculateNextPosition(stand, forward, side, arcCenter, ticks, radius, maxTicks);

                if (handleBlockCollision(stand, next, side, arcCenter)) {
                    returning = true;
                }

                stand.teleport(next);

                animateStand(stand, ticks);
                playTrailEffects(stand, ticks);

                hits += handleEntityCollision(player, stand, damage, hitEntities, returning, side, arcCenter);

                if (hits >= 3) returning = true;

                if (returning && stand.getLocation().distance(player.getLocation()) < 1.5) {
                    returnSaber(player, saber, stand);
                    cancel();
                }

                ticks++;
                if (ticks > maxTicks) returning = true;
            }

        }.runTaskTimer(MandoMC.getInstance(), 0L, 1L);
    }

    private void removeOneFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
    }

    private ArmorStand spawnSaberStand(Player player, ItemStack saber) {
        return player.getWorld().spawn(player.getLocation().add(0, 0.6, 0), ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setSmall(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setArms(true);
            s.getEquipment().setItemInMainHand(saber);
            s.setRightArmPose(new EulerAngle(Math.toRadians(40), 0, Math.toRadians(110)));
        });
    }

    private Location calculateNextPosition(ArmorStand stand, Vector forward, Vector side,
                                           Vector arcCenter, int ticks, double radius, int maxTicks) {

        double progress = ticks / (double) maxTicks;
        double angle = progress * Math.PI;

        Vector offset = side.clone().multiply(Math.cos(angle) * radius)
                .add(forward.clone().multiply(Math.sin(angle) * radius));

        offset.setY(Math.sin(angle) * 0.5);

        Vector target = arcCenter.clone().add(offset);
        Vector move = target.subtract(stand.getLocation().toVector()).multiply(0.45);

        return stand.getLocation().clone().add(move);
    }

    private boolean handleBlockCollision(ArmorStand stand, Location next, Vector side, Vector arcCenter) {

        if (!next.getBlock().getType().isSolid()) return false;

        stand.getWorld().playSound(next, "melee.lightsaber.hit", SoundCategory.PLAYERS, 1f, 0.9f);

        stand.getWorld().spawnParticle(Particle.SWEEP_ATTACK, next, 10);
        stand.getWorld().spawnParticle(Particle.CRIT, next, 18);
        stand.getWorld().spawnParticle(
                Particle.BLOCK,
                next,
                20,
                0.2, 0.2, 0.2,
                next.getBlock().getBlockData()
        );

        side.multiply(-1);

        arcCenter.add(new Vector(
                (random.nextDouble() - 0.5) * 0.5,
                0,
                (random.nextDouble() - 0.5) * 0.5
        ));

        return true;
    }

    private void animateStand(ArmorStand stand, int ticks) {
        EulerAngle rot = stand.getRightArmPose();
        double lean = 100 + Math.sin(ticks * 0.3) * 30;

        stand.setRightArmPose(
                new EulerAngle(
                        rot.getX() + Math.toRadians(45),
                        0,
                        Math.toRadians(lean)
                )
        );
    }

    private void playTrailEffects(ArmorStand stand, int ticks) {

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
    }

    private int handleEntityCollision(Player player, ArmorStand stand, double damage,
                                      Set<UUID> hitEntities, boolean returning,
                                      Vector side, Vector arcCenter) {

        int hits = 0;

        for (Entity entity : stand.getNearbyEntities(1.2, 1.2, 1.2)) {

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

            playHitEffects(living);

            hits++;

            if (!returning) {
                side.multiply(-1);
                arcCenter.add(randomOffset());
            }
        }

        return hits;
    }

    private void playHitEffects(LivingEntity entity) {

        entity.getWorld().playSound(
                entity.getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1f
        );

        entity.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                entity.getLocation().add(0, 1, 0),
                12
        );

        entity.getWorld().spawnParticle(
                Particle.CRIT,
                entity.getLocation().add(0, 1, 0),
                18
        );
    }

    private Vector randomOffset() {
        return new Vector(
                (random.nextDouble() - 0.5) * 0.5,
                0,
                (random.nextDouble() - 0.5) * 0.5
        );
    }

    /**
     * Returns the saber to the player.
     */
    private void returnSaber(Player player, ItemStack saber, ArmorStand stand) {

        if (!stand.isDead()) stand.remove();

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(saber);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), saber);
        }

        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                10,
                0.3, 0.3, 0.3,
                0.05
        );
    }
}