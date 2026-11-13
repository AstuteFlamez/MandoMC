package net.mandomc.gameplay.arrival;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Arrival validation/first-join chat; the explicit tutorial command delivers the native handbook. */
public final class ArrivalListener implements Listener {
    private final JavaPlugin plugin;
    private final ArrivalDestinations destinations;
    private final NamespacedKey tutorialSeen;
    private final List<Component> tutorial;
    private final Map<String, Component> messages = new HashMap<>();
    private final org.bukkit.Location firstSpawn;
    private final net.mandomc.gameplay.tutorial.TutorialHandbook handbook;

    public ArrivalListener(JavaPlugin plugin, ArrivalSettings settings, ArrivalDestinations destinations,
            YamlConfiguration language) {
        this.plugin = plugin;
        this.handbook = new net.mandomc.gameplay.tutorial.TutorialHandbook(plugin);
        this.destinations = destinations;
        this.firstSpawn = destinations.spawn(); // Snapshot on the main thread for the async connection event.
        this.tutorialSeen = new NamespacedKey(plugin, "arrival_tutorial_v1");
        var mini = MiniMessage.miniMessage();
        this.tutorial = language.getStringList("tutorial").stream().map(mini::deserialize).toList();
        if (tutorial.isEmpty()) {
            throw new IllegalArgumentException("language/messages.yml: tutorial must contain at least one message");
        }
        for (var key : List.of("unavailable", "players-only")) {
            var message = language.getString(key);
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("language/messages.yml: missing " + key);
            }
            messages.put(key, mini.deserialize(message));
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void firstSpawn(io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent event) {
        if (event.isNewPlayer()) event.setSpawnLocation(firstSpawn.clone());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void respawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!event.isBedSpawn() && !event.isAnchorSpawn()
                && event.getRespawnReason() == org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH) {
            event.setRespawnLocation(destinations.spawn());
        }
    }

    public boolean tutorial(CommandSender sender) {
        return handbook.command(sender);
    }

    private void showTutorial(Player player) {
        tutorial.forEach(player::sendMessage);
    }

}
