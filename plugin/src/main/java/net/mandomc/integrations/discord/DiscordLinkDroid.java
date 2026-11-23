package net.mandomc.integrations.discord;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.travel.SavedLocation;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

/** C0-M5 owns only its mannequin/title; the existing bridge owns account proof. */
public final class DiscordLinkDroid implements Listener, AutoCloseable {
    private final MandoPlugin plugin;
    private final DiscordBridge bridge;
    private final YamlConfiguration config;
    private final SavedLocation fixedAnchor;
    private final org.bukkit.scheduler.BukkitTask task;
    private Mannequin body;
    private TextDisplay title;
    private Chunk ticket;
    private Location anchor;
    public DiscordLinkDroid(MandoPlugin plugin, DiscordBridge bridge, YamlConfiguration config) {
        this.plugin = plugin; this.bridge = bridge; this.config = config;
        if (config.getInt("schema-version") != 1 || config.getString("skin.texture", "").isBlank()
                || config.getString("skin.signature", "").isBlank()) throw new IllegalArgumentException("C0-M5 requires a pinned signed skin");
        var site = config.getConfigurationSection("anchor");
        if (site == null && config.contains("anchor")) throw new IllegalArgumentException("C0-M5 anchor must be a location");
        fixedAnchor = site == null ? null : new SavedLocation(site.getString("world", ""),
                site.getDouble("x", Double.NaN), site.getDouble("y", Double.NaN), site.getDouble("z", Double.NaN),
                (float) site.getDouble("yaw", Double.NaN), (float) site.getDouble("pitch", Double.NaN));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 10);
    }
    public Location location() { return body == null ? null : body.getLocation(); }
    public TextDisplay title() { return title; }
    /** Prefer the configured six-block offset, then find clear supported ground on that same spawn level. */
    public static Location placement(Location guide, double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || Math.hypot(dx, dz) < 5 || Math.hypot(dx, dz) > 10)
            throw new IllegalArgumentException("C0-M5 must stand five to ten blocks from M4-ND");
        var candidates = new ArrayList<Location>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            var site = guide.clone().add(dx + x, 0, dz + z);
            double distance = site.distanceSquared(guide);
            if (distance >= 25 && distance <= 100) candidates.add(site);
        }
        Location preferred = guide.clone().add(dx, 0, dz);
        candidates.sort(Comparator.comparingDouble(site -> site.distanceSquared(preferred)));
        return candidates.stream().filter(site -> site.getWorld().getWorldBorder().isInside(site)
                && site.getBlock().isPassable() && !site.getBlock().isLiquid()
                && site.clone().add(0, 1, 0).getBlock().isPassable() && !site.clone().add(0, 1, 0).getBlock().isLiquid()
                && site.clone().add(0, 2, 0).getBlock().isPassable()
                && site.clone().subtract(0, .1, 0).getBlock().getType().isSolid()
                && site.getWorld().getNearbyEntities(site, 2, 2, 2).stream().noneMatch(e -> e instanceof Mannequin))
                .findFirst().orElse(null);
    }
    private Location spawnLocation() {
        if (fixedAnchor != null) {
            if (Bukkit.getWorld(NamespacedKey.fromString(fixedAnchor.world())) == null) return null;
            return fixedAnchor.resolve();
        }
        Location guide = plugin.droid() == null ? null : plugin.droid().location();
        return guide == null ? null : placement(guide, config.getDouble("offset.x", 6), config.getDouble("offset.z", 0));
    }
    private void tick() {
        if (body == null || !body.isValid() || title == null || !title.isValid()) {
            if (body != null) body.remove(); if (title != null) title.remove();
            if (anchor == null) {
                anchor = spawnLocation();
                if (anchor == null) return;
                ticket = anchor.getChunk(); ticket.addPluginChunkTicket(plugin);
            }
            body = plugin.territories().spawnNpc(EntityType.MANNEQUIN, anchor,
                    () -> anchor.getWorld().spawn(anchor, Mannequin.class, entity -> {
                var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes("mandomc:protocol-droid:c0-m5".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", config.getString("skin.texture"), config.getString("skin.signature")));
                entity.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(profile));
                entity.setDescription(Component.empty()); entity.setImmovable(true); entity.setGravity(false); entity.setInvulnerable(true);
                entity.setSilent(true); entity.setPersistent(false); entity.setRemoveWhenFarAway(false); entity.setCollidable(false);
                entity.setCanPickupItems(false); entity.setCustomNameVisible(false);
            }));
            if (body == null) return;
            title = anchor.getWorld().spawn(anchor.clone().add(0, 2.25, 0), TextDisplay.class, entity -> {
                entity.text(MiniMessage.miniMessage().deserialize(config.getString("title")));
                entity.setDefaultBackground(false); entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setBillboard(Display.Billboard.CENTER); entity.setShadowed(true); entity.setPersistent(false);
                entity.setInvulnerable(true); entity.setGravity(false); entity.setViewRange(.5f);
            });
            plugin.getLogger().info("C0-M5 linking droid ready in " + anchor.getWorld().getKey() + ".");
        }
        var nearest = body.getWorld().getPlayers().stream().filter(p -> !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                && p.getLocation().distanceSquared(anchor) < 25).min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(anchor)));
        nearest.ifPresent(player -> {
            var look = anchor.clone().setDirection(player.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector()));
            body.setRotation(look.getYaw(), look.getPitch()); body.setBodyYaw(look.getYaw());
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event) {
        if (body == null || !event.getRightClicked().getUniqueId().equals(body.getUniqueId())) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || player.getGameMode() == GameMode.SPECTATOR || player.isDead()
                || !player.getWorld().equals(body.getWorld()) || player.getLocation().distanceSquared(body.getLocation()) > 36
                || net.mandomc.gameplay.tutorial.TutorialInventory.active(player) || plugin.combat().tagged(player)) return;
        bridge.code(player);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent event) { interact(event); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent event) {
        if (body != null && event.getEntity().getUniqueId().equals(body.getUniqueId())) event.setCancelled(true);
    }
    @Override public void close() {
        task.cancel(); HandlerList.unregisterAll(this);
        if (body != null) body.remove(); if (title != null) title.remove();
        if (ticket != null) ticket.removePluginChunkTicket(plugin);
    }
}
