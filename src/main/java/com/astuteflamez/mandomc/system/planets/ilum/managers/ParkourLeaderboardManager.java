package com.astuteflamez.mandomc.system.planets.ilum.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.system.planets.ilum.TimeFormatter;
import com.astuteflamez.mandomc.system.planets.ilum.configs.ParkourConfig;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourTimeManager.PlayerTime;

public class ParkourLeaderboardManager {

    private final MandoMC plugin;
    private final ParkourTimeManager timeManager;

    private final List<TextDisplay> displays = new ArrayList<>();

    // 🔥 TAG (used for cleanup)
    private static final String TAG = "mandomc_pk_lb";

    public ParkourLeaderboardManager(MandoMC plugin, ParkourTimeManager timeManager) {
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

        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) return;

        Location loc = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
        );

        int limit = section.getInt("limit", 10);

        // 🔥 CLEANUP OLD DISPLAYS (from restart)
        cleanupOldDisplays(loc);

        List<PlayerTime> top = timeManager.getTop(limit);

        spawnLine(loc.clone().add(0, 2.2, 0), "§6§lParkour Leaderboard");

        for (int i = 0; i < limit; i++) {

            String text;

            if (i < top.size()) {

                PlayerTime pt = top.get(i);

                if (permission != null) {

                    var player = Bukkit.getOfflinePlayer(UUID.fromString(pt.uuid));

                    if (!player.isOnline() ||
                            player.getPlayer() == null ||
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

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class);

        // 🔥 TAGGING (CRITICAL)
        display.addScoreboardTag(TAG);

        display.setText(text);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);

        displays.add(display);
    }

    private void clearBoards() {
        displays.forEach(Entity::remove);
        displays.clear();
    }

    // 🔥 CLEANUP OLD DISPLAYS ON STARTUP
    private void cleanupOldDisplays(Location base) {

        if (base.getWorld() == null) return;

        for (Entity entity : base.getWorld().getNearbyEntities(base, 5, 5, 5)) {
            if (entity instanceof TextDisplay display &&
                display.getScoreboardTags().contains(TAG)) {

                display.remove();
            }
        }
    }

    // 🔥 CALL THIS IN onDisable
    public void removeAllDisplays() {
        clearBoards();
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