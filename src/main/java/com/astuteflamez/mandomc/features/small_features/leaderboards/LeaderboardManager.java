package com.astuteflamez.mandomc.features.small_features.leaderboards;

import java.util.*;

import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.astuteflamez.mandomc.MandoMC;

public class LeaderboardManager {

    private final MandoMC plugin;
    private final List<Leaderboard> boards = new ArrayList<>();

    public LeaderboardManager(MandoMC plugin) {
        this.plugin = plugin;

        plugin.getLogger().info("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Initializing leaderboards...");

        loadBoards();
        startUpdater();

        plugin.getLogger().info("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Loaded " + boards.size() + " leaderboards.");
    }

    private void loadBoards() {

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("leaderboards");

        if (section == null) {
            plugin.getLogger().warning("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7No 'leaderboards' section found in config!");
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

            String worldName = loc.getString("world");
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7World not found: " + worldName);
                continue;
            }

            Location location = new Location(
                    world,
                    loc.getDouble("x"),
                    loc.getDouble("y"),
                    loc.getDouble("z")
            );

            boards.add(new Leaderboard(id, placeholder, permission, location));

            plugin.getLogger().info("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Loaded board '" + id + "'");
        }
    }

    private void startUpdater() {

        int interval = plugin.getConfig().getInt("leaderboards.update-interval", 20);

        plugin.getLogger().info("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Update interval: " + interval + " seconds");

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

            // Permission filtering for global boards
            if (!board.getPermission().isEmpty()) {

                if (!offline.isOnline()) continue;

                Player player = offline.getPlayer();

                if (player == null || !player.hasPermission(board.getPermission())) {
                    continue;
                }
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

        // Sort descending
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Render board
        LeaderboardRenderer.render(board, entries);
    }
}