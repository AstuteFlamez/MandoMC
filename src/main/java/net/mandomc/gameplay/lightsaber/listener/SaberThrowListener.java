package net.mandomc.gameplay.lightsaber.listener;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.gameplay.lightsaber.SaberItemUtil;
import net.mandomc.gameplay.lightsaber.SaberThrowMath;
import net.mandomc.gameplay.lightsaber.config.LightsaberConfig;
import net.mandomc.server.items.ItemUtils;
import net.mandomc.server.items.config.ItemsConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Handles lightsaber throwing ability.
 */
public class SaberThrowListener implements Listener {

    private final LightsaberConfig config;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, ActiveThrow> activeThrows = new HashMap<>();
    private final Random random = new Random();

    public SaberThrowListener(LightsaberConfig config) {
        this.config = config;
    }

    @EventHandler
    public void throwLightsaber(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isValidThrow(player, item, event.getAction())) return;
        if (activeThrows.containsKey(player.getUniqueId())) return;

        ConfigurationSection stats = getStats(item);
        if (stats == null) return;

        double damage = stats.getDouble("throw_damage", 5.0);
        int cooldownTicks = Math.max(0, stats.getInt("throw_cooldown", 40));

        if (isOnCooldown(player)) {
            sendCooldownMessage(player);
            return;
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownTicks * 50L);
        launchSaber(player, item.clone(), damage);
    }

    private boolean isValidThrow(Player player, ItemStack item, Action action) {
        return SaberItemUtil.isSaberShield(item)
                && player.isSneaking()
                && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
    }

    private ConfigurationSection getStats(ItemStack item) {
        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);
        return section != null ? section.getConfigurationSection("stats") : null;
    }

    private boolean isOnCooldown(Player player) {
        return cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    private void sendCooldownMessage(Player player) {
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remainingMs = Math.max(0L, until - now);
        long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingMs / 1000.0));
        player.sendMessage(LangManager.get("lightsabers.throw-cooldown", "%seconds%", String.valueOf(remainingSeconds)));
    }

    private void launchSaber(Player player, ItemStack saber, double damage) {
        removeOneFromHand(player);
        ArmorStand stand = spawnSaberStand(player, saber);

        UUID playerId = player.getUniqueId();
        Vector forward = player.getLocation().getDirection().normalize();
        Vector side = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (side.lengthSquared() == 0) side = new Vector(1, 0, 0);

        ThrowRuntime runtime = new ThrowRuntime(
                playerId,
                stand,
                forward,
                side,
                damage,
                new HashSet<>(),
                stand.getLocation().toVector().add(forward.clone().multiply(config.getThrowForwardCenterDistance()))
        );

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                tickThrow(runtime);
            }
        }.runTaskTimer(MandoMC.getInstance(), 0L, 1L);

        activeThrows.put(playerId, new ActiveThrow(saber, stand, task));
    }

    private void removeOneFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) return;

        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
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

    private void tickThrow(ThrowRuntime runtime) {
        ActiveThrow active = activeThrows.get(runtime.playerId);
        if (active == null) return;

        ArmorStand stand = active.stand;
        if (stand.isDead() || runtime.forceFinish) {
            finishThrow(runtime.playerId, true);
            return;
        }

        Player player = MandoMC.getInstance().getServer().getPlayer(runtime.playerId);
        if (player == null || !player.isOnline() || player.isDead() || !player.getWorld().equals(stand.getWorld())) {
            finishThrow(runtime.playerId, false);
            return;
        }

        if (isSolid(stand.getLocation())) {
            runtime.solidTicks++;
            runtime.returning = true;
            if (runtime.solidTicks > config.getThrowMaxSolidRecoveryTicks()) {
                runtime.forceFinish = true;
            }
        } else {
            runtime.solidTicks = 0;
        }

        runtime.totalTicks++;
        if (runtime.totalTicks > config.getThrowMaxLifetimeTicks()) {
            finishThrow(runtime.playerId, true);
            return;
        }

        if (runtime.returning) {
            runtime.returnTicks++;
            runtime.arcCenter = player.getLocation().toVector()
                    .add(runtime.forward.clone().multiply(config.getThrowForwardCenterDistance()));
            if (runtime.returnTicks > config.getThrowMaxReturnLifetimeTicks()) {
                finishThrow(runtime.playerId, true);
                return;
            }
        }

        Location next = runtime.returning
                ? calculateReturnPosition(player, stand)
                : calculateNextPosition(stand, runtime);
        Location safe = runtime.returning
                ? resolveReturnSafety(player, stand, next, runtime)
                : resolveSafeTeleport(stand, next, runtime);
        stand.teleport(safe);

        animateStand(stand, runtime.totalTicks);
        playTrailEffects(stand, runtime.totalTicks);

        runtime.hits += handleEntityCollision(player, stand, runtime);
        if (runtime.hits >= config.getThrowMaxHitsBeforeReturn()) {
            runtime.returning = true;
        }

        if (!runtime.returning && runtime.travelTicks >= config.getThrowMaxTicks()) {
            runtime.returning = true;
        }

        if (runtime.returning && stand.getLocation().distance(player.getLocation()) < config.getThrowReturnDistance()) {
            finishThrow(runtime.playerId, true);
            return;
        }

        runtime.travelTicks++;
    }

    private Location calculateNextPosition(ArmorStand stand, ThrowRuntime runtime) {
        int maxTicks = config.getThrowMaxTicks();
        double progress = Math.min(1.0, runtime.travelTicks / (double) maxTicks);
        Vector targetOffset = SaberThrowMath.targetOffset(
                progress,
                runtime.side,
                runtime.forward,
                config.getThrowRadius(),
                config.getThrowArcHeight()
        );
        Vector move = SaberThrowMath.moveDelta(
                stand.getLocation().toVector(),
                runtime.arcCenter,
                targetOffset,
                config.getThrowMoveMultiplier()
        );
        return stand.getLocation().clone().add(move);
    }

    private Location calculateReturnPosition(Player player, ArmorStand stand) {
        Location target = player.getLocation().add(0, 0.8, 0);
        Vector delta = target.toVector().subtract(stand.getLocation().toVector());
        if (delta.lengthSquared() <= 0.0001) return stand.getLocation();

        double returnMultiplier = Math.max(0.4, Math.min(0.9, config.getThrowMoveMultiplier() + 0.2));
        return stand.getLocation().clone().add(delta.multiply(returnMultiplier));
    }

    private Location resolveReturnSafety(Player player, ArmorStand stand, Location next, ThrowRuntime runtime) {
        if (!isSolid(next)) return next;

        runtime.solidTicks++;
        if (runtime.solidTicks > config.getThrowMaxSolidRecoveryTicks()) {
            return player.getLocation().add(0, 0.8, 0);
        }
        return stand.getLocation().clone().add(0, 0.5, 0);
    }

    private Location resolveSafeTeleport(ArmorStand stand, Location next, ThrowRuntime runtime) {
        Location current = stand.getLocation();
        Vector path = next.toVector().subtract(current.toVector());
        double distance = path.length();
        if (distance <= 0.0001) return next;

        Vector direction = path.clone().normalize();
        double traceDistance = distance + config.getThrowCollisionLookahead();
        RayTraceResult hit = stand.getWorld().rayTraceBlocks(
                current,
                direction,
                traceDistance,
                FluidCollisionMode.NEVER,
                true
        );

        if (hit == null || hit.getHitPosition() == null) return next;

        runtime.returning = true;
        runtime.side.multiply(-1);
        runtime.arcCenter.add(randomOffset());

        Location hitLoc = hit.getHitPosition().toLocation(stand.getWorld());
        playBlockCollisionEffects(stand, hitLoc);

        Location safe = hitLoc.clone().subtract(direction.multiply(config.getThrowCollisionRayStep()));
        if (isSolid(safe)) {
            runtime.solidTicks++;
            if (runtime.solidTicks > config.getThrowMaxSolidRecoveryTicks()) {
                runtime.forceFinish = true;
            }
            return current;
        }
        return safe;
    }

    private int handleEntityCollision(Player player, ArmorStand stand, ThrowRuntime runtime) {
        int hits = 0;
        double range = config.getThrowEntityCollisionRange();

        for (Entity entity : stand.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == player) continue;
            if (runtime.hitEntities.contains(entity.getUniqueId())) continue;

            runtime.hitEntities.add(entity.getUniqueId());
            living.damage(runtime.damage, player);

            Vector knockback = stand.getLocation().toVector().subtract(living.getLocation().toVector());
            if (knockback.lengthSquared() > 0.0001) {
                knockback = knockback.normalize().multiply(-0.4);
            }
            living.setVelocity(knockback);

            playHitEffects(living);
            hits++;

            if (!runtime.returning) {
                runtime.side.multiply(-1);
                runtime.arcCenter.add(randomOffset());
            }
        }

        return hits;
    }

    private void playBlockCollisionEffects(ArmorStand stand, Location location) {
        stand.getWorld().playSound(location, "melee.lightsaber.hit", SoundCategory.PLAYERS, 1f, 0.9f);
        stand.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 10);
        stand.getWorld().spawnParticle(Particle.CRIT, location, 18);
        stand.getWorld().spawnParticle(
                Particle.BLOCK,
                location,
                20,
                0.2, 0.2, 0.2,
                location.getBlock().getBlockData()
        );
    }

    private void animateStand(ArmorStand stand, int ticks) {
        EulerAngle rot = stand.getRightArmPose();
        double lean = 100 + Math.sin(ticks * 0.3) * 30;
        stand.setRightArmPose(new EulerAngle(rot.getX() + Math.toRadians(45), 0, Math.toRadians(lean)));
    }

    private void playTrailEffects(ArmorStand stand, int ticks) {
        if (ticks % 3 == 0) {
            stand.getWorld().spawnParticle(Particle.CRIT, stand.getLocation(), 3, 0.05, 0.05, 0.05, 0.01);
            stand.getWorld().spawnParticle(Particle.END_ROD, stand.getLocation(), 2, 0.03, 0.03, 0.03, 0.0);
        }
        if (ticks % config.getThrowTrailSoundIntervalTicks() == 0) {
            stand.getWorld().playSound(stand.getLocation(), "melee.lightsaber.throw", SoundCategory.PLAYERS, 0.9f, 1f);
        }
    }

    private void playHitEffects(LivingEntity entity) {
        entity.getWorld().playSound(entity.getLocation(), "melee.lightsaber.hit", SoundCategory.PLAYERS, 1f, 1f);
        entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1, 0), 12);
        entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 18);
    }

    private boolean isSolid(Location location) {
        return location.getBlock().getType().isSolid();
    }

    private Vector randomOffset() {
        return new Vector((random.nextDouble() - 0.5) * 0.5, 0, (random.nextDouble() - 0.5) * 0.5);
    }

    private void finishThrow(UUID playerId, boolean returnToPlayer) {
        ActiveThrow active = activeThrows.remove(playerId);
        if (active == null) return;

        active.task.cancel();
        Player player = returnToPlayer ? MandoMC.getInstance().getServer().getPlayer(playerId) : null;
        returnSaber(player, active.saber, active.stand);
    }

    private void returnSaber(Player player, ItemStack saber, ArmorStand stand) {
        World world = stand != null ? stand.getWorld() : null;
        Location standLocation = stand != null ? stand.getLocation() : null;
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }

        if (player != null && player.isOnline() && world != null && player.getWorld().equals(world)) {
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
            return;
        }

        if (world != null && standLocation != null) {
            world.dropItemNaturally(standLocation, saber);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        finishThrow(event.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        finishThrow(event.getEntity().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        finishThrow(event.getPlayer().getUniqueId(), false);
    }

    public void shutdown() {
        for (UUID playerId : new HashSet<>(activeThrows.keySet())) {
            finishThrow(playerId, false);
        }
    }

    private static final class ActiveThrow {
        private final ItemStack saber;
        private final ArmorStand stand;
        private final BukkitTask task;

        private ActiveThrow(ItemStack saber, ArmorStand stand, BukkitTask task) {
            this.saber = saber;
            this.stand = stand;
            this.task = task;
        }
    }

    private static final class ThrowRuntime {
        private final UUID playerId;
        private final ArmorStand stand;
        private final Vector forward;
        private final Set<UUID> hitEntities;
        private final double damage;
        private Vector side;
        private Vector arcCenter;
        private int travelTicks = 0;
        private int totalTicks = 0;
        private int returnTicks = 0;
        private int hits = 0;
        private int solidTicks = 0;
        private boolean returning = false;
        private boolean forceFinish = false;

        private ThrowRuntime(UUID playerId, ArmorStand stand, Vector forward, Vector side,
                             double damage, Set<UUID> hitEntities, Vector arcCenter) {
            this.playerId = playerId;
            this.stand = stand;
            this.forward = forward;
            this.side = side;
            this.damage = damage;
            this.hitEntities = hitEntities;
            this.arcCenter = arcCenter;
        }
    }
}