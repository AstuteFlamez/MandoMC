package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the main lottery hologram.
 *
 * Displays:
 * - Current pot
 * - Time remaining until next draw
 *
 * This hologram is recreated on each update.
 */
public class LotteryHologramManager {

    private static String hologramId;

    /**
     * Updates or recreates the lottery hologram.
     *
     * Pulls data from config and replaces placeholders
     * with live lottery values.
     */
    public static void update() {

        ConfigurationSection section = LotteryConfig.get()
                .getConfigurationSection("lottery.hologram");

        if (section == null || !section.getBoolean("enabled")) {
            return;
        }

        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) {
            return;
        }

        Location location = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
        ).add(0, 2.2, 0);

        List<String> lines = buildLines(section);

        hologramId = section.getString("id", "lottery_main");

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        // Remove existing hologram if present
        manager.getHologram(hologramId).ifPresent(manager::removeHologram);

        // Create new hologram
        TextHologramData data = new TextHologramData(hologramId, location);
        data.setText(lines);
        data.setBillboard(Display.Billboard.CENTER);
        data.setTextShadow(true);
        data.setBackground(Color.fromARGB(0, 0, 0, 10));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    /**
     * Removes the hologram if it exists.
     */
    public static void remove() {

        if (hologramId == null) {
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(hologramId).ifPresent(manager::removeHologram);
    }

    /**
     * Builds formatted hologram lines from config.
     *
     * Replaces placeholders with live values:
     * - %pot%
     * - %time%
     *
     * @param section hologram config section
     * @return formatted lines
     */
    private static List<String> buildLines(ConfigurationSection section) {

        List<String> rawLines = section.getStringList("lines");
        List<String> result = new ArrayList<>();

        String pot = String.valueOf(LotteryManager.getPot());
        String time = LotteryScheduler.getTimeRemaining();

        for (String line : rawLines) {
            result.add(color(
                    line.replace("%pot%", pot)
                        .replace("%time%", time)
            ));
        }

        return result;
    }

    /**
     * Applies color formatting to a string.
     *
     * @param text input text
     * @return colored text
     */
    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}