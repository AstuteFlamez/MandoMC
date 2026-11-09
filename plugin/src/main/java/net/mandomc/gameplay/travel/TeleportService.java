package net.mandomc.gameplay.travel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** One main-thread scheduler and one pending request per player, shared by all travel entry points. */
public final class TeleportService implements Listener, AutoCloseable {
    private record Pending(Player player, Location origin, Supplier<Location> destination, long due) { }
    private record Spiral(Player player, Location center, long started) { }
    private final JavaPlugin plugin;
    private final YamlConfiguration language;
    private final int seconds;
    private final net.mandomc.gameplay.combat.CombatService combat;
    private final int spiralTicks;
    private final Sound tickSound;
    private final Sound arrivalSound;
    private final Sound cancelSound;
    private final float volume;
    private final boolean effects;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Spiral> spirals = new HashMap<>();
    private final BukkitTask ticker;
    private long clock;

    public TeleportService(JavaPlugin plugin, YamlConfiguration config, YamlConfiguration language,
                           net.mandomc.gameplay.combat.CombatService combat) {
        this.combat = combat;
        this.plugin = plugin;
        this.language = language;
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("teleport.yml: expected schema-version 1");
        seconds = config.getInt("warmup-seconds", -1);
        spiralTicks = config.getInt("effects.spiral-ticks", -1);
        double configuredVolume = config.getDouble("effects.volume", -1);
        if (seconds < 1 || seconds > 30
                || spiralTicks < 1 || spiralTicks > 100 || !Double.isFinite(configuredVolume)
                || configuredVolume < 0 || configuredVolume > 2) {
            throw new IllegalArgumentException("teleport.yml: invalid warmup (1..30), "
                    + "spiral ticks (1..100), or volume (0..2)");
        }
        volume = (float) configuredVolume;
        effects = config.getBoolean("effects.enabled", true);
        try {
            tickSound = sound(config, "effects.countdown-sound");
            arrivalSound = sound(config, "effects.arrival-sound");
            cancelSound = sound(config, "effects.cancel-sound");
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("teleport.yml: unknown effects sound", error);
        }
        for (var key : new String[] {"countdown-title", "countdown-subtitle", "success-title", "success-subtitle",
                "moved", "damaged", "interrupted", "unavailable", "combat", "mounted", "pending", "players-only"}) {
            if (!language.isString("teleport." + key) || language.getString("teleport." + key).isBlank()) {
                throw new IllegalArgumentException("messages.yml: missing teleport." + key);
            }
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private static Sound sound(YamlConfiguration config, String path) {
        var key = org.bukkit.NamespacedKey.fromString(config.getString(path, ""));
        var sound = key == null ? null : org.bukkit.Registry.SOUNDS.get(key);
        if (sound == null) throw new IllegalArgumentException("teleport.yml: unknown sound at " + path);
        return sound;
    }

    public Component message(String key) { return text(key, seconds); }

    private Component text(String key, int remaining) {
        return MiniMessage.miniMessage().deserialize(language.getString("teleport." + key),
                Placeholder.unparsed("seconds", Integer.toString(remaining)));
    }

    public boolean request(Player player, Supplier<Location> destination) {
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(message("pending"));
            return false;
        }
        String refusal = refusal(player);
        if (refusal != null) {
            player.sendMessage(message(refusal));
            return false;
        }
        try {
            if (destination.get() == null) throw new IllegalStateException("Missing destination");
        } catch (IllegalStateException error) {
            unavailable(player, error);
            return false;
        }
        spirals.remove(player.getUniqueId());
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return complete(player, destination);
        }
        pending.put(player.getUniqueId(), new Pending(player, player.getLocation().clone(), destination,
                clock + seconds * 20L));
        countdown(player, seconds);
        return true;
    }

    private String refusal(Player player) {
        if (!player.isOnline() || player.isDead()) return "interrupted";
        if (combat.tagged(player)) return "combat";
        if (player.isInsideVehicle() || !player.getPassengers().isEmpty()) return "mounted";
        return null;
    }

    /** Combat adapters must call this for attributed PvP that does not emit normal Bukkit damage events. */
    public void tagCombat(Player player) {
        combat.tag(player);
        cancel(player, "combat", true);
    }

    private void countdown(Player player, int remaining) {
        player.showTitle(Title.title(text("countdown-title", remaining), text("countdown-subtitle", remaining),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(100))));
        if (effects) player.playSound(player.getLocation(), tickSound, volume,
                0.8f + 0.8f * (seconds - remaining) / Math.max(1, seconds - 1));
    }

    private void tick() {
        clock++;
        for (var request : java.util.List.copyOf(pending.values())) {
            var player = request.player();
            String refusal = refusal(player);
            if (refusal != null) {
                cancel(player, refusal, player.isOnline());
            } else if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                pending.remove(player.getUniqueId());
                complete(player, request.destination());
            } else if (hasMoved(request.origin(), player.getLocation())) {
                cancel(player, "moved", true);
            } else if (clock >= request.due()) {
                pending.remove(player.getUniqueId()); // Own teleport must not cancel its own request.
                complete(player, request.destination());
            } else if ((request.due() - clock) % 20 == 0) {
                countdown(player, (int) ((request.due() - clock) / 20));
            }
        }
        var iterator = spirals.values().iterator();
        while (iterator.hasNext()) {
            var spiral = iterator.next();
            long age = clock - spiral.started();
            var player = spiral.player();
            if (age >= spiralTicks || !player.isOnline() || player.isDead()
                    || !player.getWorld().equals(spiral.center().getWorld())
                    || player.getLocation().distanceSquared(spiral.center()) > 16) {
                iterator.remove();
                continue;
            }
            double progress = (double) age / spiralTicks;
            for (int strand = 0; strand < 2; strand++) {
                double angle = progress * Math.PI * 6 + strand * Math.PI;
                var point = spiral.center().clone().add(Math.cos(angle) * 0.75, progress * 2.1,
                        Math.sin(angle) * 0.75);
                player.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Creative skips only the countdown; validation, consent and cancellable teleport events still apply. */
    private boolean complete(Player player, Supplier<Location> destination) {
        player.clearTitle();
        try {
            var location = destination.get();
            if (location == null) throw new IllegalStateException("Missing destination");
            if (!player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                player.sendMessage(message("unavailable"));
                return false;
            }
            player.showTitle(Title.title(message("success-title"), message("success-subtitle"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(700), Duration.ofMillis(350))));
            if (effects) {
                player.getWorld().playSound(player.getLocation(), arrivalSound, volume, 1.2f);
                spirals.put(player.getUniqueId(), new Spiral(player, player.getLocation().clone(), clock));
            }
            return true;
        } catch (IllegalStateException error) {
            unavailable(player, error);
            return false;
        }
    }

    /** Position only: camera yaw/pitch are intentionally ignored. A tiny tolerance absorbs coordinate noise. */
    public static boolean hasMoved(Location from, Location to) {
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        return !java.util.Objects.equals(from.getWorld(), to.getWorld()) || dx * dx + dy * dy + dz * dz > 0.000001;
    }

    private void unavailable(Player player, IllegalStateException error) {
        player.sendMessage(message("unavailable"));
        plugin.getLogger().warning("Teleport refused: " + error.getMessage());
    }

    public void cancelRequest(Player player) { cancel(player, "interrupted", true); }

    private void cancel(Player player, String reason, boolean notify) {
        if (pending.remove(player.getUniqueId()) != null && notify) {
            player.clearTitle();
            player.sendMessage(message(reason));
            if (effects) player.playSound(player.getLocation(), cancelSound, volume, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var request = pending.get(event.getPlayer().getUniqueId());
        if (request != null && hasMoved(request.origin(), event.getTo())) cancel(event.getPlayer(), "moved", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer(), "interrupted", true);
        spirals.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (event.getEntity() instanceof Player player) {
            cancel(player, "damaged", true);
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        cancel(event.getEntity(), "interrupted", false);
        spirals.remove(event.getEntity().getUniqueId());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), "interrupted", false);
        spirals.remove(event.getPlayer().getUniqueId());
    }

    @Override public void close() {
        ticker.cancel();
        for (var request : pending.values()) {
            if (request.player().isOnline()) request.player().clearTitle();
        }
        pending.clear();
        spirals.clear();
    }
}
