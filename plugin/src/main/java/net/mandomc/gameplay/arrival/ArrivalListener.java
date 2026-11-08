package net.mandomc.gameplay.arrival;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** The first route is one-way from protected SpawnWorld; general travel belongs to the group/combat slice. */
public final class ArrivalListener implements Listener {
    private final JavaPlugin plugin;
    private final ArrivalSettings settings;
    private final ArrivalDestinations destinations;
    private final NamespacedKey tutorialSeen;
    private final List<Component> tutorial;
    private final Map<String, Component> messages = new HashMap<>();
    private final Map<UUID, BukkitTask> departures = new HashMap<>();

    public ArrivalListener(JavaPlugin plugin, ArrivalSettings settings, ArrivalDestinations destinations,
            YamlConfiguration language) {
        this.plugin = plugin;
        this.settings = settings;
        this.destinations = destinations;
        this.tutorialSeen = new NamespacedKey(plugin, "arrival_tutorial_v1");
        var mini = MiniMessage.miniMessage();
        this.tutorial = language.getStringList("tutorial").stream().map(mini::deserialize).toList();
        if (tutorial.isEmpty()) {
            throw new IllegalArgumentException("messages.yml: tutorial must contain at least one message");
        }
        for (var key : List.of("unavailable", "players-only", "from-spawn", "dismount", "departing",
                "cancelled", "arrived", "already-departing")) {
            var message = language.getString(key);
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("messages.yml: missing " + key);
            }
            messages.put(key, mini.deserialize(message.replace("{seconds}", "" + settings.warmupSeconds())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        try {
            destinations.validate();
        } catch (IllegalStateException error) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, messages.get("unavailable"));
            plugin.getLogger().severe(error.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPack(PlayerResourcePackStatusEvent event) {
        var player = event.getPlayer();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
                && !player.getPersistentDataContainer().has(tutorialSeen, PersistentDataType.BYTE)) {
            showTutorial(player);
            player.getPersistentDataContainer().set(tutorialSeen, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean tutorial(CommandSender sender) {
        if (sender instanceof Player player) {
            showTutorial(player);
        } else {
            sender.sendMessage(messages.get("players-only"));
        }
        return true;
    }

    private void showTutorial(Player player) {
        tutorial.forEach(player::sendMessage);
    }

    public boolean depart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("players-only"));
            return true;
        }
        if (departures.containsKey(player.getUniqueId())) {
            player.sendMessage(messages.get("already-departing"));
            return true;
        }
        try {
            destinations.validate();
            if (!player.getWorld().getKey().toString().equals(settings.spawnWorld())) {
                player.sendMessage(messages.get("from-spawn"));
                return true;
            }
            if (player.isInsideVehicle() || !player.getPassengers().isEmpty()) {
                player.sendMessage(messages.get("dismount"));
                return true;
            }
            var origin = player.getLocation();
            player.sendMessage(messages.get("departing"));
            departures.put(player.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                departures.remove(player.getUniqueId());
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                if (moved(origin, player.getLocation()) || player.isInsideVehicle()
                        || !player.getPassengers().isEmpty()) {
                    player.sendMessage(messages.get("cancelled"));
                    return;
                }
                try {
                    destinations.validate();
                    if (player.teleport(destinations.survival(), PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                        player.sendMessage(messages.get("arrived"));
                    } else {
                        player.sendMessage(messages.get("unavailable"));
                    }
                } catch (IllegalStateException error) {
                    unavailable(player, error);
                }
            }, settings.warmupSeconds() * 20L));
        } catch (IllegalStateException error) {
            unavailable(player, error);
        }
        return true;
    }

    private void unavailable(Player player, IllegalStateException error) {
        player.sendMessage(messages.get("unavailable"));
        plugin.getLogger().warning(error.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (moved(event.getFrom(), event.getTo())) {
            cancel(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancel(player, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer(), false);
    }

    private static boolean moved(Location from, Location to) {
        return !from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > 0.0001;
    }

    private void cancel(Player player, boolean notify) {
        var task = departures.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (notify) {
                player.sendMessage(messages.get("cancelled"));
            }
        }
    }

    public void close() {
        departures.values().forEach(BukkitTask::cancel);
        departures.clear();
    }
}
