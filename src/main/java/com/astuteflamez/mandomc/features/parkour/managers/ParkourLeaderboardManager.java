package com.astuteflamez.mandomc.features.parkour.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import com.astuteflamez.mandomc.features.parkour.TimeFormatter;
import com.astuteflamez.mandomc.features.parkour.configs.ParkourConfig;
import com.astuteflamez.mandomc.features.parkour.managers.ParkourTimeManager.PlayerTime;

public class ParkourLeaderboardManager {

    private final Plugin plugin;
    private final ParkourTimeManager timeManager;

    private final List<TextDisplay> displays = new ArrayList<>();

    public ParkourLeaderboardManager(Plugin plugin, ParkourTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    public void updateLeaderboards() {

        clearBoards();

        ConfigurationSection section =
                ParkourConfig.get().getConfigurationSection("parkour.leaderboards");

        if (section == null) return;

        createBoard(section.getConfigurationSection("global"), null);

        ConfigurationSection skilled = section.getConfigurationSection("skilled");

        createBoard(
                skilled,
                skilled != null ? skilled.getString("permission") : null
        );
    }

    private void createBoard(ConfigurationSection section, String permission) {

        if (section == null) return;

        String worldName = section.getString("world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) return;

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");

        int limit = section.getInt("limit", 10);

        Location loc = new Location(world, x, y, z);

        List<PlayerTime> top = timeManager.getTop(limit);

        spawnLine(loc.clone().add(0, 2.2, 0), "§6§lParkour Leaderboard");

        for (int i = 0; i < limit; i++) {

            String text;

            if (i < top.size()) {

                PlayerTime pt = top.get(i);

                if (permission != null) {

                    var player = Bukkit.getOfflinePlayer(
                            java.util.UUID.fromString(pt.uuid)
                    );

                    if (!player.isOnline() ||
                            !player.getPlayer().hasPermission(permission)) {

                        text = "§e" + (i + 1) + ".";
                    } else {

                        text = "§e" + (i + 1) + ". §f" + pt.name +
                                " §7- §a" + TimeFormatter.format(pt.best_time);
                    }

                } else {

                    text = "§e" + (i + 1) + ". §f" + pt.name +
                            " §7- §a" + TimeFormatter.format(pt.best_time);
                }

            } else {

                text = "§e" + (i + 1) + ".";
            }

            spawnLine(loc.clone().add(0, 2 - i * 0.25, 0), text);
        }
    }

    private void spawnLine(Location loc, String text) {

        TextDisplay display = (TextDisplay) loc.getWorld()
                .spawnEntity(loc, EntityType.TEXT_DISPLAY);

        display.setText(text);

        display.setShadowed(true);

        display.setBackgroundColor(Color.fromARGB(0,0,0,0));

        display.setBillboard(Display.Billboard.CENTER);

        display.setSeeThrough(false);

        displays.add(display);
    }

    private void clearBoards() {

        for (TextDisplay display : displays) {
            display.remove();
        }

        displays.clear();
    }

    public void startAutoUpdate() {

        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updateLeaderboards,
                20L * 10,
                20L * 60
        );
    }
}