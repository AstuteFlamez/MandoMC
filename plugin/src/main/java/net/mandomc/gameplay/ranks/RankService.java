package net.mandomc.gameplay.ranks;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** One native authority for UUID memberships and Bukkit permission attachments. */
public final class RankService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final RankCatalog catalog;
    private final Path file;
    private volatile Map<UUID, Set<String>> users;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private boolean saving;
    private volatile boolean closed;
    private final org.bukkit.scheduler.BukkitTask refresh;
    private Set<String> registered = Set.of();
    public RankService(JavaPlugin plugin, org.bukkit.configuration.ConfigurationSection config) throws Exception {
        this.plugin = plugin;
        catalog = new RankCatalog(config);
        file = plugin.getDataFolder().toPath().resolve("data/rank-users.yml");
        users = read(file, catalog);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::apply);
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            var next = Bukkit.getPluginManager().getPermissions().stream().map(p -> p.getName()).collect(java.util.stream.Collectors.toSet());
            if (!next.equals(registered)) { registered = next; Bukkit.getOnlinePlayers().forEach(this::apply); }
        }, 1, 20);
    }
    public static Map<UUID, Set<String>> read(Path file, RankCatalog catalog) throws Exception {
        if (!Files.exists(file)) return Map.of();
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        if (yaml.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("rank-users.yml: expected schema-version 1");
        var section = yaml.getConfigurationSection("users");
        if (section == null) throw new IllegalArgumentException("rank-users.yml: missing users");
        var result = new HashMap<UUID, Set<String>>();
        for (String key : section.getKeys(false)) {
            if (!section.isList(key + ".ranks")) throw new IllegalArgumentException("rank-users.yml: missing ranks for " + key);
            var ranks = Set.copyOf(section.getStringList(key + ".ranks"));
            ranks.forEach(catalog::get);
            result.put(UUID.fromString(key), ranks);
        }
        return Map.copyOf(result);
    }
    public RankCatalog catalog() { return catalog; }
    public Set<String> memberships(UUID id) { return users.getOrDefault(id, Set.of("default")); }
    public RankCatalog.Rank rank(Player player) { return catalog.primary(memberships(player.getUniqueId())); }
    public void change(UUID id, Set<String> ranks, Consumer<Boolean> done) {
        ranks.forEach(catalog::get);
        if (saving || closed) { done.accept(false); return; }
        saving = true;
        var next = new HashMap<>(users); next.put(id, Set.copyOf(ranks));
        var yaml = new YamlConfiguration(); yaml.set("schema-version", 1); yaml.createSection("users");
        next.forEach((uuid, assigned) -> yaml.set("users." + uuid + ".ranks", assigned.stream().sorted().toList()));
        String snapshot = yaml.saveToString();
        writer.submit(() -> {
            boolean success = false;
            try {
                net.mandomc.config.AtomicData.write(file, snapshot);
                users = Map.copyOf(next); success = true;
            } catch (Exception error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save rank-users.yml; change rejected", error); }
            boolean saved = success;
            if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                saving = false;
                var player = Bukkit.getPlayer(id);
                if (saved && player != null) { apply(player); player.updateCommands(); }
                done.accept(saved);
            });
        });
    }
    public void apply(Player player) {
        remove(player);
        var attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);
        var nodes = catalog.permissions(memberships(player.getUniqueId()));
        var candidates = new TreeSet<>(nodes.keySet());
        for (var permission : Bukkit.getPluginManager().getPermissions()) {
            candidates.add(permission.getName()); candidates.addAll(permission.getChildren().keySet());
        }
        // Expand wildcard nodes into the permissions registered by installed plugins.
        for (String node : candidates) {
            Boolean value = RankCatalog.resolve(nodes, node);
            if (value != null) attachment.setPermission(node, value);
        }
        player.recalculatePermissions();
    }
    private void remove(Player player) {
        var old = attachments.remove(player.getUniqueId());
        if (old != null) player.removeAttachment(old);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent event) {
        // Paper retains message signatures and the existing event recipients; only the renderer changes.
        var rank = rank(event.getPlayer());
        event.renderer((source, name, message, viewer) ->
                net.mandomc.gameplay.messaging.PlayerChat.render(rank, name, message));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void join(PlayerJoinEvent event) { apply(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) { remove(event.getPlayer()); }
    @Override public void close() {
        closed = true; refresh.cancel(); HandlerList.unregisterAll(this);
        Bukkit.getOnlinePlayers().forEach(this::remove);
        writer.shutdown();
        try { if (!writer.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().severe("Rank save is still finishing."); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
