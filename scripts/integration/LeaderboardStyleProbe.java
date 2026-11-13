package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.mandomc.gameplay.ilum.ParkourService;
import net.mandomc.gameplay.leaderboards.WorldLeaderboards;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Presentation/lifecycle regression; run only against an isolated server fixture. */
public final class LeaderboardStyleProbe extends JavaPlugin {
    private int checks;
    private void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private void verifyBoard(TextDisplay board, double baseline) {
        check(board != null && board.isValid(), "Board exists");
        check(Math.abs(board.getLocation().getY() - baseline) < .00001, "Raised baseline independent of populated rows");
        check(!board.isDefaultBackground() && board.getBackgroundColor().getAlpha() == 0, "Transparent background");
        check(board.getBillboard() == Display.Billboard.VERTICAL, "Upright viewer tracking");
        check(board.getLineWidth() == 300, "Long player names fit without extra wrapped rows");
        var lines = PlainTextComponentSerializer.plainText().serialize(board.text()).split("\n", -1);
        check(lines.length == 13, "Fixed title/subtitle/spacer/ten rows");
        check(lines[1].equals("TOP TEN PLAYERS") && lines[2].isEmpty(), "Reference subtitle and spacing");
        for (int rank = 1; rank <= 10; rank++) check(lines[rank + 2].startsWith(rank + "."), "Numbered slot " + rank);
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var owner = Objects.requireNonNull(Bukkit.getServicesManager().load(WorldLeaderboards.class));
                var tag = new NamespacedKey("mandomc", "world_leaderboard");
                var boards = Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getEntitiesByClass(TextDisplay.class)
                        .stream().filter(e -> e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)).toList();
                check(boards.size() == 2, "Exactly two world boards");
                for (var board : boards) {
                    verifyBoard(board, 96.4125);
                    var before = board.text(); owner.refresh();
                    check(before.equals(board.text()) && board.isValid(), "Refresh preserves identity and fixed layout");
                }
                var parkour = Objects.requireNonNull(Bukkit.getServicesManager().load(ParkourService.class));
                var field = ParkourService.class.getDeclaredField("leaderboard"); field.setAccessible(true);
                verifyBoard((TextDisplay) field.get(parkour), -57.325);
                var missing = boards.getFirst(); missing.remove(); owner.refresh();
                check(boards.stream().filter(Entity::isValid).count() == 1, "Other world board retained");
                var fresh = missing.getWorld().getEntitiesByClass(TextDisplay.class).stream()
                        .filter(e -> e.getPersistentDataContainer().has(tag) && !e.getUniqueId().equals(boards.getLast().getUniqueId()))
                        .findFirst().orElseThrow();
                verifyBoard(fresh, 96.4125);
                getDataFolder().mkdirs();
                Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"), "LEADERBOARD STYLE PASSED " + checks);
                getLogger().info("LEADERBOARD STYLE PASSED " + checks);
            } catch (Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE, "Leaderboard style failed", failure); }
            finally { Bukkit.shutdown(); }
        }, 120);
    }
}
