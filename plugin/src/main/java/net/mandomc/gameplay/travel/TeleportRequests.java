package net.mandomc.gameplay.travel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.mandomc.commands.CommandMessages;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.combat.CombatService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** A request is consent between two UUIDs, consumed once, then handed to the shared warmup. */
public final class TeleportRequests implements Listener, AutoCloseable {
    public record Request(UUID sender, UUID recipient, boolean here, long expires) { }
    private final Map<UUID, Request> outgoing = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final TeleportService travel;
    private final CombatService combat;
    private final CommandMessages messages;
    private final long expiryNanos;
    private final long cooldownNanos;
    private final int maxIncoming;
    private final BukkitTask ticker;
    private final java.util.function.Function<UUID, Player> players;
    private final java.util.function.LongSupplier clock;

    public TeleportRequests(JavaPlugin plugin, TeleportService travel, CombatService combat,
                            CommandMessages messages, ConfigurationSection config) {
        this(plugin, travel, combat, messages, config, Bukkit::getPlayer, System::nanoTime);
    }

    public TeleportRequests(JavaPlugin plugin, TeleportService travel, CombatService combat,
                            CommandMessages messages, ConfigurationSection config,
                            java.util.function.Function<UUID, Player> players, java.util.function.LongSupplier clock) {
        this.players = players; this.clock = clock;
        this.travel = travel; this.combat = combat; this.messages = messages;
        int expiry = config.getInt("request-expiry-seconds", -1);
        int cooldown = config.getInt("request-cooldown-seconds", -1);
        maxIncoming = config.getInt("max-incoming-requests", -1);
        if (expiry < 5 || expiry > 300 || cooldown < 1 || cooldown > 60 || maxIncoming < 1 || maxIncoming > 100) {
            throw new IllegalArgumentException("utilities.yml: invalid request expiry/cooldown/capacity");
        }
        expiryNanos = expiry * 1_000_000_000L; cooldownNanos = cooldown * 1_000_000_000L;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20, 20);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean send(Player from, Player to, boolean here) {
        expire();
        if (from.getUniqueId().equals(to.getUniqueId())) { messages.send(from, "self"); return false; }
        try { participants(from, to); }
        catch (IllegalStateException error) { messages.send(from, "request-unavailable"); return false; }
        if (outgoing.containsKey(from.getUniqueId())) { messages.send(from, "request-pending"); return false; }
        long now = clock.getAsLong();
        if (now < cooldowns.getOrDefault(from.getUniqueId(), 0L)) { messages.send(from, "request-cooldown"); return false; }
        if (incoming(to).size() >= maxIncoming) { messages.send(from, "request-full"); return false; }
        outgoing.put(from.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), here, now + expiryNanos));
        cooldowns.put(from.getUniqueId(), now + cooldownNanos);
        messages.send(from, "request-sent", to.getName());
        messages.send(to, here ? "request-here" : "request-to", from.getName());
        return true;
    }

    public List<Request> incoming(Player recipient) {
        return outgoing.values().stream().filter(r -> r.recipient().equals(recipient.getUniqueId()))
                .filter(r -> clock.getAsLong() < r.expires()).sorted(Comparator.comparingLong(Request::expires)).toList();
    }

    public Request select(Player recipient, String senderName) {
        expire();
        var requests = incoming(recipient);
        if (senderName == null && requests.size() > 1) throw new IllegalArgumentException("request-choose");
        return requests.stream().filter(r -> senderName == null || players.apply(r.sender()) != null
                && players.apply(r.sender()).getName().equalsIgnoreCase(senderName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("request-missing"));
    }

    public boolean accept(Player recipient, String senderName) {
        var request = select(recipient, senderName);
        var sender = players.apply(request.sender());
        var mover = request.here() ? recipient : sender;
        var anchor = request.here() ? sender : recipient;
        if (sender == null || !sender.hasPermission(request.here() ? "mandomc.travel.tpahere" : "mandomc.travel.tpa")) {
            outgoing.remove(request.sender()); messages.send(recipient, "request-unavailable"); return false;
        }
        boolean queued = travel.request(mover, () -> {
            participants(mover, anchor);
            if (!recipient.hasPermission("mandomc.travel.tpaccept")
                    || !sender.hasPermission(request.here() ? "mandomc.travel.tpahere" : "mandomc.travel.tpa")) {
                throw new IllegalStateException("Teleport request permission changed");
            }
            return ArrivalDestinations.safeSpawn(anchor.getLocation().clone());
        });
        if (queued) {
            outgoing.remove(request.sender());
            messages.send(recipient, "request-accepted"); messages.send(sender, "request-accepted");
        } else messages.send(recipient, "request-unavailable");
        return queued;
    }

    private void participants(Player first, Player second) {
        if (first == null || second == null || !first.isOnline() || !second.isOnline() || first.isDead()
                || second.isDead() || !first.canSee(second) || !second.canSee(first) || combat.tagged(first)
                || combat.tagged(second) || first.isInsideVehicle() || second.isInsideVehicle()
                || !first.getPassengers().isEmpty() || !second.getPassengers().isEmpty()) {
            throw new IllegalStateException("Teleport participant unavailable, mounted or in combat");
        }
    }

    public void deny(Player recipient, String senderName) {
        var request = select(recipient, senderName);
        outgoing.remove(request.sender());
        messages.send(recipient, "request-denied"); notify(request.sender(), "request-denied");
    }
    public void cancel(Player sender) {
        var request = outgoing.remove(sender.getUniqueId());
        if (request == null) throw new IllegalArgumentException("request-missing");
        messages.send(sender, "request-cancelled"); notify(request.recipient(), "request-cancelled");
    }
    private void notify(UUID id, String key) {
        var player = players.apply(id);
        if (player != null) messages.send(player, key);
    }
    private void expire() {
        long now = clock.getAsLong();
        for (var request : List.copyOf(outgoing.values())) {
            if (now >= request.expires()) {
                outgoing.remove(request.sender());
                notify(request.sender(), "request-expired"); notify(request.recipient(), "request-expired");
            }
        }
        cooldowns.values().removeIf(until -> now >= until);
    }
    private void remove(Player player) {
        for (var request : List.copyOf(outgoing.values())) {
            if (request.sender().equals(player.getUniqueId()) || request.recipient().equals(player.getUniqueId())) {
                outgoing.remove(request.sender());
                notify(request.sender().equals(player.getUniqueId()) ? request.recipient() : request.sender(), "request-cancelled");
            }
        }
        cooldowns.remove(player.getUniqueId());
    }
    @EventHandler public void quit(PlayerQuitEvent event) { remove(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) { remove(event.getEntity()); }
    @Override public void close() { ticker.cancel(); outgoing.clear(); cooldowns.clear(); HandlerList.unregisterAll(this); }
}
