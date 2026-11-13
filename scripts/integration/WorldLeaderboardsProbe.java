package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.leaderboards.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Synthetic ranking/update tests run only on an isolated Paper copy. */
public final class WorldLeaderboardsProbe extends JavaPlugin {
    private static final NamespacedKey TAG = new NamespacedKey("mandomc", "world_leaderboard");
    private int checks;
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private List<TextDisplay> boards() {
        return Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getEntitiesByClass(TextDisplay.class).stream()
                .filter(e -> e.getPersistentDataContainer().has(TAG)).toList();
    }
    private TextDisplay board(String id) {
        return boards().stream().filter(e -> id.equals(e.getPersistentDataContainer().get(TAG, PersistentDataType.STRING))).findFirst().orElseThrow();
    }
    private String text(String id) { return PlainTextComponentSerializer.plainText().serialize(board(id).text()); }
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, () -> {
        try { verify(); } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "Leaderboard verification failed", failure);
            try { getDataFolder().mkdirs(); Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), failure.toString()); }
            catch (Exception error) { throw new RuntimeException(error); }
        } finally { Bukkit.shutdown(); }
    }, 120); }
    private void verify() throws Exception {
        var plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        var owner = Objects.requireNonNull(Bukkit.getServicesManager().load(WorldLeaderboards.class));
        check(boards().size() == 2, "Exactly two owned boards at startup/restart");
        check(board("kills").getLocation().equals(new Location(board("kills").getWorld(), -59.50074498169203, 96.4125, 13.431550557182451)), "Exact saved kills anchor, raised display baseline");
        check(board("balance").getLocation().equals(new Location(board("balance").getWorld(), -63.44204842969489, 96.4125, 10.63073610449501)), "Exact saved balance anchor, raised display baseline");
        for (var board : boards()) {
            check(!board.isDefaultBackground() && board.getBackgroundColor().getAlpha() == 0, "Explicit transparent background");
            check(board.getBillboard() == Display.Billboard.VERTICAL && board.getPitch() == 0, "Upright billboard ignores captured down-look pitch");
            check(board.isShadowed() && !board.isPersistent(), "Shadow and nonpersistent lifecycle defaults");
        }
        String phase = new java.io.File(getDataFolder(), "phase2").exists() ? "restart" : "initial";
        if (phase.equals("initial")) {
            var rows = text("kills").lines().toList();
            check(rows.size() == 13, "Title, subtitle, spacer and ten ranked rows");
            for (int rank = 1; rank <= 10; rank++)
                check(rows.get(rank + 2).startsWith(rank + ". ") && rows.get(rank + 2).endsWith(": " + (13 - rank)), "Offline native kill ranking " + rank);
            check(text("balance").contains("1. ") && text("balance").contains(": $15.00"), "Offline committed ledger balances rendered in credits");
            var tie = plugin.credits().ledger().top(2);
            check(tie.get(0).getKey().toString().endsWith("00000000000b") && tie.get(1).getKey().toString().endsWith("00000000000c"), "Existing credit ledger UUID tie order retained");
        } else {
            check(text("kills").lines().skip(3).findFirst().orElseThrow().endsWith(": 100"), "Native offline kill statistic survives restart");
            check(text("balance").lines().skip(3).findFirst().orElseThrow().endsWith(": $9,999.99"), "Committed credit balance survives restart");
        }
        var unrelated = new HashMap<UUID, String>();
        for (var world : Bukkit.getWorlds()) for (var e : world.getEntitiesByClass(TextDisplay.class))
            if (!e.getPersistentDataContainer().has(TAG)) unrelated.put(e.getUniqueId(), e.text().toString());
        var ids = boards().stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Bukkit.getOfflinePlayer(first).setStatistic(Statistic.PLAYER_KILLS, 100);
        plugin.credits().ledger().adjust(first, "set", 999999);
        owner.refresh();
        check(text("kills").lines().skip(3).findFirst().orElseThrow().endsWith(": 100"), "Native stat mutation appears on existing display");
        check(text("balance").lines().skip(3).findFirst().orElseThrow().endsWith(": $9,999.99"), "Committed ledger mutation appears on existing display");
        check(ids.equals(boards().stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet())), "Refresh retains display entities");
        LeaderboardFeature.start(plugin); LeaderboardFeature.start(plugin); owner.refresh();
        check(boards().size() == 2 && Bukkit.getServicesManager().load(WorldLeaderboards.class) == owner, "Duplicate startup/refresh retains sole owner and two boards");
        var stale = board("kills"); stale.remove(); owner.refresh();
        check(boards().size() == 2 && !board("kills").getUniqueId().equals(stale.getUniqueId()), "Missing owned display recreated once");
        for (var entry : unrelated.entrySet()) check(entry.getValue().equals(((TextDisplay)Objects.requireNonNull(Bukkit.getEntity(entry.getKey()))).text().toString()), "Unrelated text unchanged");
        LeaderboardFeature.stop();
        check(boards().isEmpty() && Bukkit.getServicesManager().load(WorldLeaderboards.class) == null, "Owner shutdown removes only its boards/service");
        for (var id : unrelated.keySet()) check(Bukkit.getEntity(id) != null, "Unrelated entities retained on close");
        LeaderboardFeature.start(plugin);
        getDataFolder().mkdirs(); var result = "PASSED: " + checks + " native world leaderboard assertions (" + phase + ")\n";
        Files.writeString(getDataFolder().toPath().resolve("PASSED-" + phase + ".txt"), result); getLogger().info(result);
    }
}
