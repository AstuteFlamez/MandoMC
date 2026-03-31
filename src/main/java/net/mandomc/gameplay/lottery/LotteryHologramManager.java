package net.mandomc.gameplay.lottery;

import net.mandomc.core.LangManager;
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

import java.util.ArrayList;
import java.util.List;
import net.mandomc.core.integration.OptionalPluginSupport;
import net.mandomc.gameplay.lottery.task.LotteryScheduler;

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
    private static net.mandomc.gameplay.lottery.config.LotteryConfig lotteryConfig;

    public static void init(net.mandomc.gameplay.lottery.config.LotteryConfig cfg) {
        lotteryConfig = cfg;
    }

    /**
     * Updates or recreates the lottery hologram.
     *
     * Pulls data from config and replaces placeholders
     * with live lottery values.
     */
    public static void update() {
        HologramManager manager = getHologramManager();
        if (manager == null) {
            return;
        }

        if (lotteryConfig == null) return;
        ConfigurationSection section = lotteryConfig.getHologramSection();

        if (section == null || !section.getBoolean("enabled")) {
            remove();
            return;
        }

        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) {
            remove();
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

        manager.getHologram(hologramId).ifPresentOrElse(existing -> {
            if (existing.getData() instanceof TextHologramData textData) {
                textData.setLocation(location);
                textData.setText(lines);
                return;
            }
            manager.removeHologram(existing);
            createHologram(manager, location, lines);
        }, () -> createHologram(manager, location, lines));
    }

    /**
     * Removes the hologram if it exists.
     */
    public static void remove() {
        if (!OptionalPluginSupport.hasFancyHolograms()) {
            return;
        }

        if (hologramId == null) {
            return;
        }

        HologramManager manager = getHologramManager();
        if (manager == null) {
            return;
        }
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
        return LangManager.colorize(text);
    }

    private static void createHologram(HologramManager manager, Location location, List<String> lines) {
        TextHologramData data = new TextHologramData(hologramId, location);
        data.setText(lines);
        data.setBillboard(Display.Billboard.CENTER);
        data.setTextShadow(true);
        data.setBackground(Color.fromARGB(0, 0, 0, 10));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    private static HologramManager getHologramManager() {
        if (!OptionalPluginSupport.hasFancyHolograms()) {
            return null;
        }
        try {
            return FancyHologramsPlugin.get().getHologramManager();
        } catch (Throwable ignored) {
            return null;
        }
    }
}