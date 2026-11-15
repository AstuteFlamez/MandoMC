package net.mandomc.gameplay.leaderboards;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Read-only views of native player statistics and the committed credit ledger. */
public final class WorldLeaderboards implements AutoCloseable, Listener {
    private record Row(UUID id, String name, long value) { }
    private static final NamespacedKey TAG = new NamespacedKey("mandomc", "world_leaderboard");
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final Map<String, Location> anchors = new LinkedHashMap<>();
    private final Map<String, TextDisplay> displays = new LinkedHashMap<>();
    private final Set<Chunk> chunks = new HashSet<>();
    private final BukkitTask ticker;
    private boolean closed;

    public WorldLeaderboards(MandoPlugin plugin, YamlConfiguration config) {
        this.plugin = plugin; this.config = config;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("leaderboards/world.yml: schema-version 1 required");
        for (String id : List.of("kills", "balance")) {
            String path = "boards." + id;
            var world = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString(config.getString(path + ".world"))), "Leaderboard world missing");
            var point = config.getDoubleList(path + ".location");
            if (point.size() != 3 || point.stream().anyMatch(n -> !Double.isFinite(n)))
                throw new IllegalArgumentException(path + ".location: three finite coordinates required");
            anchors.put(id, new Location(world, point.get(0), point.get(1), point.get(2)));
        }
        for (var anchor : anchors.values()) {
            var chunk = anchor.getChunk();
            if (chunk.addPluginChunkTicket(plugin)) chunks.add(chunk);
        }
        // These tags belong only to this owner; never sweep other holograms or NPCs.
        for (var world : anchors.values().stream().map(Location::getWorld).distinct().toList())
            for (var display : world.getEntitiesByClass(TextDisplay.class))
                if (anchors.containsKey(display.getPersistentDataContainer().get(TAG, PersistentDataType.STRING))) display.remove();
        refresh();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 200, 200);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString().substring(0, 8) : name;
    }

    private List<Row> kills() {
        Map<UUID, OfflinePlayer> players = new HashMap<>();
        for (var player : Bukkit.getOfflinePlayers()) players.put(player.getUniqueId(), player);
        for (var player : Bukkit.getOnlinePlayers()) players.put(player.getUniqueId(), player);
        return players.values().stream()
                .map(player -> new Row(player.getUniqueId(), name(player.getUniqueId()), player.getStatistic(Statistic.PLAYER_KILLS)))
                .sorted(Comparator.comparingLong(Row::value).reversed().thenComparing(row -> row.id().toString()))
                .limit(10).toList();
    }

    private Component message(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... values) {
        return MiniMessage.miniMessage().deserialize(Objects.requireNonNull(config.getString("messages." + key)), values);
    }

    private TextDisplay display(String id) {
        var existing = displays.get(id);
        if (existing != null && existing.isValid()) return existing;
        var at = anchors.get(id).clone().add(0, config.getDouble("display-height-offset", .35), 0);
        var created = at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, id);
            entity.setDefaultBackground(false); entity.setBackgroundColor(Color.fromARGB(0));
            entity.setBillboard(Display.Billboard.VERTICAL); entity.setShadowed(true);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER); entity.setLineWidth(300);
            entity.setPersistent(false); entity.setViewRange(.7f);
        });
        displays.put(id, created); return created;
    }

    public void refresh() {
        if (closed) return;
        for (String id : anchors.keySet()) {
            var rows = id.equals("kills") ? kills() : plugin.credits().ledger().top(10).stream()
                    .map(entry -> new Row(entry.getKey(), name(entry.getKey()), entry.getValue())).toList();
            List<Component> lines = new ArrayList<>(); int rank = 0;
            for (var row : rows) lines.add(message(id + "-row",
                    Placeholder.unparsed("rank", Integer.toString(++rank)), Placeholder.unparsed("name", row.name()),
                    Placeholder.unparsed("value", id.equals("kills") ? Long.toString(row.value()) : CreditLedger.format(row.value()))));
            var entity = display(id);
            entity.setDefaultBackground(false); entity.setBackgroundColor(Color.fromARGB(0));
            entity.text(LeaderboardLayout.render(message(id + "-title"), message(id + "-subtitle"), lines,
                    slot -> message(id + "-empty-row", Placeholder.unparsed("rank", Integer.toString(slot)))));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    @Override public void close() {
        if (closed) return;
        closed = true; ticker.cancel(); HandlerList.unregisterAll(this);
        displays.values().forEach(TextDisplay::remove); displays.clear();
        chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin)); chunks.clear();
    }
}
