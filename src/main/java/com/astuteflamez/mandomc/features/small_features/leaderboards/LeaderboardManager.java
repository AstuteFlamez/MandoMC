package com.astuteflamez.mandomc.features.small_features.leaderboards;

import java.util.*;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import com.astuteflamez.mandomc.MandoMC;

public class LeaderboardManager {

    private final MandoMC plugin;
    private final List<Leaderboard> boards = new ArrayList<>();

    public LeaderboardManager(MandoMC plugin) {
        this.plugin = plugin;

        plugin.getLogger().info("§c[Leaderboards] Initializing...");

        loadBoards();
        startUpdater();

        plugin.getLogger().info("§c[Leaderboards] Loaded " + boards.size() + " boards.");
    }

    private void loadBoards() {

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("leaderboards");

        if (section == null) {
            plugin.getLogger().warning("No leaderboards section found!");
            return;
        }

        for (String id : section.getKeys(false)) {

            if (id.equals("update-interval")) continue;

            ConfigurationSection board = section.getConfigurationSection(id);
            if (board == null) continue;

            String placeholder = board.getString("placeholder");
            String permission = board.getString("permission-required", "");

            ConfigurationSection loc = board.getConfigurationSection("location");
            if (loc == null) continue;

            World world = Bukkit.getWorld(loc.getString("world"));
            if (world == null) continue;

            Location location = new Location(
                    world,
                    loc.getDouble("x"),
                    loc.getDouble("y"),
                    loc.getDouble("z")
            );

            Leaderboard lb = new Leaderboard(id, placeholder, permission, location);

            // 🔥 CLEANUP OLD DISPLAYS (from previous restarts)
            cleanupOldDisplays(lb);

            boards.add(lb);
        }
    }

    private void startUpdater() {

        int interval = plugin.getConfig().getInt("leaderboards.update-interval", 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Leaderboard board : boards) {
                    updateBoard(board);
                }
            }
        }.runTaskTimer(plugin, 60L, interval * 20L);
    }

    private void updateBoard(Leaderboard board) {

        List<LeaderboardEntry> entries = new ArrayList<>();

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {

            if (!offline.hasPlayedBefore()) continue;

            if (!board.getPermission().isEmpty()) {
                if (!offline.isOnline()) continue;

                Player player = offline.getPlayer();
                if (player == null || !player.hasPermission(board.getPermission())) continue;
            }

            try {
                String value = PlaceholderAPI.setPlaceholders(offline, board.getPlaceholder());
                if (value == null || value.isEmpty()) continue;

                double number = Double.parseDouble(value.replace(",", ""));

                entries.add(new LeaderboardEntry(
                        offline.getUniqueId(),
                        offline.getName(),
                        number
                ));

            } catch (Exception ignored) {}
        }

        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        LeaderboardRenderer.render(board, entries);
    }

    // 🔥 CLEANUP ON STARTUP
    private void cleanupOldDisplays(Leaderboard board) {
        Location base = board.getLocation();
        if (base.getWorld() == null) return;

        base.getWorld().getNearbyEntities(base, 5, 5, 5).forEach(entity -> {
            if (entity instanceof TextDisplay display &&
                display.getScoreboardTags().contains("mandomc_lb")) {
                display.remove();
            }
        });
    }

    // 🔥 CLEANUP ON DISABLE
    public void removeAllDisplays() {
        for (Leaderboard board : boards) {
            board.getDisplays().forEach(TextDisplay::remove);
            board.getDisplays().clear();
        }
    }
}