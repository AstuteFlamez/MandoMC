package net.mandomc.gameplay.vehicles;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.regions.VehicleProtection;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Bounded native swept bolts; one attributed damage path for each impact and splash target. */
public final class VehicleCannons implements Listener, AutoCloseable {
    private static final int MAX_BOLTS = 256;
    private record Bolt(Player shooter, VehicleService.FiringPlatform platform, VehicleWeaponSettings settings,
                        ItemDisplay display, Location location, Vector motion, double remaining) {}
    private record BlastVisual(Location location, int age) {}
    private final List<BlastVisual> effects = new ArrayList<>();
    private final JavaPlugin plugin;
    private final VehicleService vehicles;
    private final ItemCatalog items;
    private final net.mandomc.gameplay.explosions.TemporaryExplosions explosions;
    private final VehicleProtection protection;
    private final ConfigurationSection language;
    private final Map<String, VehicleWeaponSettings> weapons = new HashMap<>();
    private final Map<UUID, Long> recharge = new HashMap<>();
    private final Map<UUID, Integer> lastSwing = new HashMap<>();
    private final List<Bolt> bolts = new ArrayList<>();
    private final BukkitTask task;

    public VehicleCannons(JavaPlugin plugin, VehicleService vehicles, ItemCatalog items,
            VehicleProtection protection, net.mandomc.gameplay.explosions.TemporaryExplosions explosions,
            ConfigurationSection config, ConfigurationSection language) {
        this.plugin = plugin; this.vehicles = vehicles; this.items = items;
        this.protection = protection; this.language = language; this.explosions = explosions;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("vehicles/weapons.yml: expected schema 1");
        for (String type : vehicles.types()) {
            var weapon = VehicleWeaponSettings.read(config.getConfigurationSection("vehicles." + type));
            if (!items.ids().contains(weapon.ammo())) throw new IllegalArgumentException("Unknown vehicle ammo: " + weapon.ammo());
            weapons.put(type, weapon);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void swing(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) fire(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(PlayerInteractEvent event) {
        var action = event.getAction();
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || (action != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                    && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK)
                || vehicles.mountedType(event.getPlayer()) == null) return;
        event.setCancelled(true);
        fire(event.getPlayer());
    }

    public int activeBolts() { return bolts.size(); }

    public boolean fire(Player player) {
        var weapon = weapons.get(vehicles.mountedType(player));
        if (weapon == null || player.isDead()) return false;
        var platform = vehicles.firingPlatform(player, weapon.bones());
        if (platform == null || !protection.damage(player.getLocation())
                || !protection.damage(platform.entity().getLocation())) return false;
        long now = System.nanoTime() / 1_000_000;
        if (now < recharge.getOrDefault(player.getUniqueId(), 0L)) {
            message(player, "weapon-recharging", "seconds", String.format(Locale.ROOT, "%.1f",
                    Math.ceil((recharge.get(player.getUniqueId()) - now) / 100.0) / 10.0));
            return false;
        }
        if (lastSwing.getOrDefault(player.getUniqueId(), -1) == Bukkit.getCurrentTick()
                || bolts.size() + weapon.bones().size() > MAX_BOLTS) return false;
        for (Location muzzle : platform.muzzles())
            if (!loaded(muzzle) || !protection.damage(muzzle)) return false;
        var template = items.create(weapon.ammo());
        if (!consume(player, template, weapon.ammoCost())) {
            message(player, "weapon-ammo", "ammo", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(template.getItemMeta().displayName()));
            return false;
        }
        lastSwing.put(player.getUniqueId(), Bukkit.getCurrentTick());
        recharge.put(player.getUniqueId(), now + weapon.cooldownMillis());
        try {
            for (Location muzzle : platform.muzzles()) {
                var position = muzzle.clone();
                position.setYaw(0); position.setPitch(0);
                var motion = platform.direction().clone().normalize().multiply(weapon.speed());
                var display = muzzle.getWorld().spawn(position, ItemDisplay.class, d -> {
                    var item = new ItemStack(Material.ECHO_SHARD);
                    item.editMeta(meta -> meta.setItemModel(weapon.projectileModel()));
                    d.setItemStack(item); d.setPersistent(false); d.setInvulnerable(true);
                    d.setTeleportDuration(1); d.setViewRange(2);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setTransformation(VehicleBoltPose.facing(motion));
                });
                bolts.add(new Bolt(player, platform, weapon, display, position, motion, weapon.range()));
            }
        } catch (RuntimeException error) {
            // Failed emission refunds the volley and removes its partial projectiles.
            for (Bolt bolt : List.copyOf(bolts)) if (bolt.platform() == platform) remove(bolt);
            template.setAmount(weapon.ammoCost());
            player.getInventory().addItem(template).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            recharge.remove(player.getUniqueId());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot emit vehicle volley", error);
            return false;
        }
        player.getWorld().playSound(platform.entity().getLocation(), weapon.sound(), 1, 1);
        return true;
    }

    private static boolean consume(Player player, ItemStack template, int cost) {
        var inventory = player.getInventory();
        int found = 0;
        for (ItemStack item : inventory.getStorageContents()) if (item != null && item.isSimilar(template)) found += item.getAmount();
        if (found < cost) return false;
        for (int slot = 0; slot < inventory.getStorageContents().length && cost > 0; slot++) {
            var item = inventory.getItem(slot);
            if (item == null || !item.isSimilar(template)) continue;
            int used = Math.min(cost, item.getAmount());
            item.subtract(used); cost -= used;
            inventory.setItem(slot, item.isEmpty() ? null : item);
        }
        return true;
    }

    private boolean excluded(Bolt bolt, Entity entity) {
        return bolt.platform().excluded().contains(entity.getUniqueId())
                || vehicles.occupant(bolt.platform().id(), entity.getUniqueId());
    }

    private void tick() {
        for (BlastVisual effect : List.copyOf(effects)) {
            effects.remove(effect);
            visual(effect.location(), effect.age());
            if (effect.age() < 11) effects.add(new BlastVisual(effect.location(), effect.age() + 1));
        }
        for (Bolt bolt : List.copyOf(bolts)) {
            if (!bolt.display().isValid() || !bolt.shooter().isOnline() || bolt.shooter().isDead()
                    || bolt.shooter().getWorld() != bolt.location().getWorld()
                    || !protection.damage(bolt.shooter().getLocation())) { remove(bolt); continue; }
            double distance = Math.min(bolt.remaining(), bolt.motion().length());
            var direction = bolt.motion().clone().normalize();
            boolean clear = true;
            for (double step = 0; step <= distance + .249; step += .25) {
                var point = bolt.location().clone().add(direction.clone().multiply(Math.min(step, distance)));
                if (!loaded(point) || !protection.damage(point)) { clear = false; break; }
            }
            if (!clear) { remove(bolt); continue; }
            var hit = bolt.location().getWorld().rayTrace(bolt.location(), direction, distance,
                    FluidCollisionMode.NEVER, true, .15,
                    entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand) && !excluded(bolt, entity));
            if (hit != null) {
                var impact = hit.getHitPosition().toLocation(bolt.location().getWorld());
                impact(bolt, impact, hit.getHitEntity()); remove(bolt); continue;
            }
            var next = bolt.location().clone().add(direction.multiply(distance));
            double remaining = bolt.remaining() - distance;
            if (remaining <= 0) { remove(bolt); continue; }
            bolt.display().teleport(next);
            bolts.remove(bolt);
            bolts.add(new Bolt(bolt.shooter(), bolt.platform(), bolt.settings(), bolt.display(), next, bolt.motion(), remaining));
        }
    }

    private void impact(Bolt bolt, Location location, Entity direct) {
        var victims = new LinkedHashSet<LivingEntity>();
        if (direct instanceof LivingEntity living) victims.add(living);
        double radius = bolt.settings().radius();
        for (Entity nearby : location.getWorld().getNearbyEntities(location, radius, radius, radius))
            if (nearby instanceof LivingEntity living && !(living instanceof ArmorStand)
                    && living.getBoundingBox().getCenter().distanceSquared(location.toVector()) <= radius * radius) victims.add(living);
        for (LivingEntity victim : victims) {
            if (!victim.isValid() || victim.isDead() || victim.isInvulnerable() || excluded(bolt, victim)
                    || !protection.damage(victim.getLocation())) continue;
            var center = victim.getBoundingBox().getCenter().toLocation(victim.getWorld());
            if (!clearSplash(location, center)) continue;
            victim.damage(bolt.settings().damage(), bolt.shooter());
        }
        // Block-only shared blast path: damage above remains the sole attributed hit path.
        if (bolt.settings().terrainExplosion()) explosions.vendorBlast(location, bolt.shooter());
        visual(location, 0);
        if (effects.size() < 32) effects.add(new BlastVisual(location.clone(), 1));
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 3, .6f);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2, 1.4f);
    }

    private void visual(Location center, int phase) {
        // Recovered five-stage blast, bounded globally so rapid twin-cannon fire cannot
        // leave unbounded delayed work behind.
        switch (phase) {
            case 0 -> {
                center.getWorld().spawnParticle(Particle.EXPLOSION, center, 4, .2, .2, .2, 0);
                sphere(center, 1.2, Particle.SMOKE, 15, .04);
            }
            case 2 -> { sphere(center, 2.4, Particle.EXPLOSION, 6, 0); sphere(center, 2, Particle.SMOKE, 25, .06); }
            case 4 -> { sphere(center, 4.4, Particle.EXPLOSION, 5, 0); sphere(center, 4, Particle.SMOKE, 40, .1); }
            case 7 -> sphere(center, 5.6, Particle.SMOKE, 50, .08);
            case 11 -> center.getWorld().spawnParticle(Particle.SMOKE, center.clone().add(0, .5, 0), 25, 2.4, .8, 2.4, .02);
            default -> { }
        }
    }

    private void sphere(Location center, double radius, Particle particle, int count, double speed) {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            center.getWorld().spawnParticle(particle, center.clone().add(radius * Math.sin(phi) * Math.cos(theta),
                    radius * Math.cos(phi), radius * Math.sin(phi) * Math.sin(theta)), 1, .05, .05, .05, speed);
        }
    }

    private boolean clearSplash(Location start, Location end) {
        var delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        for (double step = .25; step < length; step += .25) {
            var point = start.clone().add(delta.clone().multiply(step / length));
            if (!loaded(point) || !protection.damage(point)) return false;
        }
        return length < .01 || start.getWorld().rayTraceBlocks(start, delta, length, FluidCollisionMode.NEVER, true) == null;
    }

    private static boolean loaded(Location point) {
        var world = point.getWorld();
        return point.getY() >= world.getMinHeight() && point.getY() < world.getMaxHeight()
                && world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4);
    }

    private void message(Player player, String key, String placeholder, String value) {
        player.sendActionBar(MiniMessage.miniMessage().deserialize(language.getString(key), Placeholder.unparsed(placeholder, value)));
    }

    private void remove(Bolt bolt) { bolts.remove(bolt); bolt.display().remove(); }

    @EventHandler public void quit(PlayerQuitEvent event) {
        recharge.remove(event.getPlayer().getUniqueId()); lastSwing.remove(event.getPlayer().getUniqueId());
        for (Bolt bolt : List.copyOf(bolts)) if (bolt.shooter().equals(event.getPlayer())) remove(bolt);
    }

    @Override public void close() {
        task.cancel();
        for (Bolt bolt : List.copyOf(bolts)) remove(bolt);
        effects.clear(); recharge.clear(); lastSwing.clear(); HandlerList.unregisterAll(this);
    }
}
