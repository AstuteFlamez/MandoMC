package net.mandomc.system.planets.ilum.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.mandomc.MandoMC;
import net.mandomc.system.planets.ilum.TimeFormatter;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;
import net.mandomc.system.planets.ilum.managers.ParkourTimeManager.PlayerTime;

public class ParkourLeaderboardManager {

    private final MandoMC plugin;
    private final ParkourTimeManager timeManager;

    private final List<String> hologramIds = new ArrayList<>();

    public ParkourLeaderboardManager(MandoMC plugin, ParkourTimeManager timeManager) {
        this.plugin = plugin;
        this.timeManager = timeManager;
    }

    public void updateLeaderboards() {

        clearBoards();

        ConfigurationSection section =
                ParkourConfig.get().getConfigurationSection("parkour.leaderboards");

        if (section == null) return;

        createBoard("global", section.getConfigurationSection("global"), null);

        ConfigurationSection skilled = section.getConfigurationSection("skilled");

        createBoard(
                "skilled",
                skilled,
                skilled != null ? skilled.getString("permission") : null
        );
    }

    private void createBoard(String id, ConfigurationSection section, String permission) {

        if (section == null) return;

        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) return;

        Location loc = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
        ).add(0, 2.2, 0);

        int limit = section.getInt("limit", 10);

        List<PlayerTime> top = timeManager.getTop(limit);
        List<String> lines = new ArrayList<>();

        /* -------------------------
           HEADER (UPDATED STYLE)
        ------------------------- */
        lines.add(color("&3❄ &lɪʟᴜᴍ ʟᴇᴀᴅᴇʀʙᴏᴀʀᴅ &r&3❄"));
        lines.add(color("&3ᴛᴏᴘ ᴘᴀʀᴋᴏᴜʀ ʀᴜɴɴᴇʀѕ"));
        lines.add(" ");

        /* -------------------------
           ENTRIES
        ------------------------- */
        for (int i = 0; i < limit; i++) {

            String text;

            if (i < top.size()) {

                PlayerTime pt = top.get(i);

                if (permission != null) {

                    var player = Bukkit.getOfflinePlayer(UUID.fromString(pt.uuid));

                    if (!player.isOnline() ||
                            player.getPlayer() == null ||
                            !player.getPlayer().hasPermission(permission)) {

                        text = color("&3" + (i + 1) + ".");

                    } else {

                        text = color("&3" + (i + 1) + ". &b" + pt.name +
                                " &7- &3" + TimeFormatter.format(pt.best_time));
                    }

                } else {

                    text = color("&3" + (i + 1) + ". &b" + pt.name +
                            " &7- &3" + TimeFormatter.format(pt.best_time));
                }

            } else {
                text = color("&3" + (i + 1) + ".");
            }

            lines.add(text);
        }

        String hologramId = "parkour_lb_" + id;

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        // remove existing
        manager.getHologram(hologramId).ifPresent(manager::removeHologram);

        // create hologram
        TextHologramData data = new TextHologramData(hologramId, loc);
        data.setText(lines);
        data.setTextShadow(true);
        data.setBackground(Color.fromARGB(0, 0, 0, 10));
        data.setBillboard(Display.Billboard.CENTER);

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);

        hologramIds.add(hologramId);
    }

    private void clearBoards() {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        for (String id : hologramIds) {
            manager.getHologram(id).ifPresent(manager::removeHologram);
        }

        hologramIds.clear();
    }

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

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}