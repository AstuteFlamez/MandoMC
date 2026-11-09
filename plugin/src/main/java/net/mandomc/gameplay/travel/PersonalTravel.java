package net.mandomc.gameplay.travel;

import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Home and return locations are saved with the player's native persistent data. */
public final class PersonalTravel implements Listener {
    private final NamespacedKey homesKey;
    private final NamespacedKey backKey;
    private final int limit;
    private final java.util.function.Predicate<Player> correction;

    public PersonalTravel(JavaPlugin plugin, int limit, java.util.function.Predicate<Player> correction) {
        this.correction = correction;
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("admin/utilities.yml: homes-limit must be 1..100");
        this.limit = limit;
        homesKey = new NamespacedKey(plugin, "homes_v1");
        backKey = new NamespacedKey(plugin, "back_v1");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    public static String homeName(String input) {
        String value = input.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]{1,24}")) throw new IllegalArgumentException("home-name");
        return value;
    }
    private PersistentDataContainer homes(Player player) {
        var data = player.getPersistentDataContainer();
        var homes = data.get(homesKey, PersistentDataType.TAG_CONTAINER);
        return homes != null ? homes : data.getAdapterContext().newPersistentDataContainer();
    }
    private NamespacedKey key(String name) { return new NamespacedKey("mandomc", homeName(name)); }
    public List<String> names(Player player) {
        return homes(player).getKeys().stream().map(NamespacedKey::getKey).sorted().toList();
    }
    public void set(Player player, String name) {
        var homes = homes(player);
        var key = key(name);
        if (!homes.has(key) && homes.getKeys().size() >= limit && !player.hasPermission("mandomc.travel.homes.unlimited")) {
            throw new IllegalArgumentException("home-limit");
        }
        homes.set(key, PersistentDataType.STRING, SavedLocation.of(player.getLocation()).encode());
        player.getPersistentDataContainer().set(homesKey, PersistentDataType.TAG_CONTAINER, homes);
    }
    public void delete(Player player, String name) {
        var homes = homes(player);
        var key = key(name);
        if (!homes.has(key)) throw new IllegalArgumentException("home-missing");
        homes.remove(key);
        player.getPersistentDataContainer().set(homesKey, PersistentDataType.TAG_CONTAINER, homes);
    }
    public SavedLocation home(Player player, String name) {
        var value = homes(player).get(key(name), PersistentDataType.STRING);
        if (value == null) throw new IllegalArgumentException("home-missing");
        return SavedLocation.decode(value);
    }
    public SavedLocation back(Player player) {
        var value = player.getPersistentDataContainer().get(backKey, PersistentDataType.STRING);
        if (value == null) throw new IllegalArgumentException("back-missing");
        return SavedLocation.decode(value);
    }
    public void remember(Player player, org.bukkit.Location location) {
        player.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, SavedLocation.of(location).encode());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        // Movement corrections and pearls must not overwrite the last deliberate trip/death location.
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        if (!event.getPlayer().isDead() && !correction.test(event.getPlayer()) && TeleportService.hasMoved(event.getFrom(), event.getTo())) {
            remember(event.getPlayer(), event.getFrom());
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) {
        remember(event.getEntity(), event.getEntity().getLocation());
    }
}
