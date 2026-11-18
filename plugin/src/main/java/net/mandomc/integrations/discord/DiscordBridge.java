package net.mandomc.integrations.discord;

import com.google.gson.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.credits.CreditService;
import net.mandomc.gameplay.ranks.RankService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Only immutable snapshots cross the network executor. RankService remains the sole rank writer. */
public final class DiscordBridge implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final JavaPlugin plugin;
    private final RankService ranks;
    private final CreditService credits;
    private final ConfigurationSection messages;
    private final DiscordSettings settings;
    private final String secret;
    private final HttpClient http;
    private final ExecutorService network;
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final Map<UUID, Long> codeCooldown = new HashMap<>();
    private final org.bukkit.scheduler.BukkitTask task;
    private volatile List<UUID> linked = List.of();
    private volatile boolean closed;
    private int cursor;
    private long lastWarning;

    public DiscordBridge(JavaPlugin plugin, RankService ranks, CreditService credits,
            ConfigurationSection config, ConfigurationSection messages) throws Exception {
        this.plugin = plugin; this.ranks = ranks; this.credits = credits; this.messages = messages;
        settings = DiscordSettings.read(config, ranks.catalog());
        String value = System.getenv("MANDOMC_DISCORD_BRIDGE_SECRET");
        if (value == null || value.isBlank()) {
            Path path = plugin.getDataFolder().toPath().resolve("private/discord-bridge-secret");
            value = Files.readString(path).trim();
        }
        if (value.length() < 32 || value.contains("\n") || value.contains("\r")) throw new IllegalArgumentException("Discord bridge secret must have at least 32 characters");
        secret = value;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        network = Executors.newSingleThreadExecutor();
        Objects.requireNonNull(plugin.getCommand("discord")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player && args.length == 0) code(player);
            else sender.sendMessage(MiniMessage.miniMessage().deserialize(messages.getString("usage")));
            return true;
        });
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, settings.intervalSeconds() * 20L);
    }

    private void tell(Player player, String key, String code) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(messages.getString(key), Placeholder.unparsed("code", code)));
    }

    private void code(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - codeCooldown.getOrDefault(id, 0L) < 30000) { tell(player, "cooldown", ""); return; }
        codeCooldown.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
        codeCooldown.put(id, now);
        String name = player.getName();
        tell(player, "requesting", "");
        network.submit(() -> {
            try {
                var response = request("/bridge/code", Map.of("uuid", id.toString(), "username", name));
                onMain(() -> {
                    var current = Bukkit.getPlayer(id);
                    if (current != null) tell(current, response.has("linked") ? "linked" : "code",
                            response.has("code") ? response.get("code").getAsString() : "");
                });
            } catch (Exception error) {
                warn(); onMain(() -> { var current = Bukkit.getPlayer(id); if (current != null) tell(current, "unavailable", ""); });
            }
        });
    }

    private void tick() {
        if (closed || !syncing.compareAndSet(false, true)) return;
        var players = new ArrayList<Map<String, Object>>();
        List<UUID> ids = linked;
        for (int n = 0; n < Math.min(10, ids.size()); n++) {
            UUID id = ids.get(Math.floorMod(cursor++, ids.size()));
            players.add(Map.of("uuid", id.toString(), "ranks", ranks.memberships(id)));
        }
        var top = new ArrayList<Map<String, Object>>();
        for (var entry : credits.ledger().top(10)) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name != null) top.add(Map.of("username", name, "cents", entry.getValue()));
        }
        var snapshot = Map.of("online", Bukkit.getOnlinePlayers().size(), "maximum", Bukkit.getMaxPlayers(), "baltop", top);
        network.submit(() -> {
            try {
                request("/bridge/snapshot", snapshot);
                var next = new ArrayList<UUID>(); String after = "";
                // Paginate persisted links, including offline accounts; never infer account ownership by name.
                do {
                    var page = request("/bridge/links" + (after.isEmpty() ? "" : "?after=" + after), null);
                    for (var value : page.getAsJsonArray("uuids")) next.add(UUID.fromString(value.getAsString()));
                    after = page.get("next").isJsonNull() ? "" : page.get("next").getAsString();
                    if (next.size() > 10000) throw new IllegalStateException("Discord link limit");
                } while (!after.isEmpty());
                linked = List.copyOf(next);
                var updates = new ArrayList<JsonObject>();
                for (int start = 0; start < players.size(); start += 2) {
                    var response = request("/bridge/sync", Map.of("mapping", settings.mapping(), "players", players.subList(start, Math.min(start + 2, players.size()))));
                    if (!response.getAsJsonArray("errors").isEmpty()) warn();
                    for (var value : response.getAsJsonArray("updates")) if (!value.getAsJsonObject().has("absent")) updates.add(value.getAsJsonObject());
                }
                onMain(() -> apply(updates, 0));
            } catch (Exception error) { warn(); syncing.set(false); }
        });
    }

    private static Set<String> strings(JsonArray values) {
        var result = new HashSet<String>(); for (var value : values) result.add(value.getAsString()); return Set.copyOf(result);
    }

    private void apply(List<JsonObject> updates, int index) {
        if (closed || index >= updates.size()) { syncing.set(false); return; }
        try {
            var update = updates.get(index); UUID id = UUID.fromString(update.get("uuid").getAsString());
            if (!linked.contains(id)) throw new IllegalArgumentException("Unlinked rank update");
            var current = ranks.memberships(id);
            var next = DiscordSettings.merge(current, strings(update.getAsJsonArray("expected")),
                    strings(update.getAsJsonArray("ranks")), settings.mapping().keySet());
            if (next == null || next.equals(current)) { apply(updates, index + 1); return; }
            // RankService rejects a concurrent save. The durable remote intent is retried next cycle.
            ranks.change(id, next, saved -> {
                if (saved) plugin.getLogger().info("Discord rank synchronization applied for " + id);
                apply(updates, index + 1);
            });
        } catch (RuntimeException error) { warn(); apply(updates, index + 1); }
    }

    private JsonObject request(String path, Object body) throws Exception {
        if (closed) throw new IllegalStateException("closed");
        var builder = HttpRequest.newBuilder(settings.endpoint().resolve(path)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + secret).header("Content-Type", "application/json");
        if (body == null) builder.GET(); else builder.POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("Discord bridge HTTP " + response.statusCode());
            byte[] bytes = input.readNBytes(262145);
            if (bytes.length > 262144) throw new IllegalStateException("Discord bridge response too large");
            return JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void onMain(Runnable work) {
        if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) work.run(); });
    }

    private synchronized void warn() {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 300000) return;
        lastWarning = now;
        plugin.getLogger().warning("Discord bridge unavailable or configuration mismatch; existing ranks retained. Retrying automatically.");
    }

    @Override public void close() {
        closed = true; task.cancel(); network.shutdownNow(); http.shutdownNow();
    }
}
