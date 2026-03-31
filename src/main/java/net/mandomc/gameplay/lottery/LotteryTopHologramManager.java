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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.mandomc.core.integration.OptionalPluginSupport;

/**
 * Handles creation and updating of the lottery top players hologram.
 *
 * Displays the top 3 players based on ticket count along with
 * their number of tickets and win probability percentage.
 */
public class LotteryTopHologramManager {

    private static net.mandomc.gameplay.lottery.config.LotteryConfig lotteryConfig;
    private static String hologramId;
    private static final Map<UUID, String> playerNameCache = new HashMap<>();

    public static void init(net.mandomc.gameplay.lottery.config.LotteryConfig cfg) {
        lotteryConfig = cfg;
    }

    /**
     * Updates or recreates the top lottery hologram.
     * Pulls data from config and LotteryManager.
     */
    public static void update() {
        HologramManager manager = getHologramManager();
        if (manager == null) {
            return;
        }

        if (lotteryConfig == null) return;
        ConfigurationSection section = lotteryConfig.getTopHologramSection();

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

        Map<UUID, Integer> ticketMap = LotteryManager.getAllTickets();

        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(ticketMap.entrySet());
        sortedEntries.sort((entryA, entryB) -> Integer.compare(entryB.getValue(), entryA.getValue()));

        int totalTickets = ticketMap.values().stream().mapToInt(Integer::intValue).sum();

        List<String> formattedLines = buildLines(section, sortedEntries, totalTickets);

        hologramId = section.getString("id", "lottery_top");

        manager.getHologram(hologramId).ifPresentOrElse(existing -> {
            if (existing.getData() instanceof TextHologramData textData) {
                textData.setLocation(location);
                textData.setText(formattedLines);
                return;
            }
            manager.removeHologram(existing);
            createHologram(manager, location, formattedLines);
        }, () -> createHologram(manager, location, formattedLines));
    }

    /**
     * Builds the hologram lines by replacing placeholders with player data.
     *
     * @param section config section
     * @param sorted sorted ticket entries
     * @param total total ticket count
     * @return formatted lines
     */
    private static List<String> buildLines(ConfigurationSection section,
                                           List<Map.Entry<UUID, Integer>> sorted,
                                           int total) {

        List<String> rawLines = section.getStringList("lines");
        List<String> result = new ArrayList<>();

        for (String line : rawLines) {

            line = applyPlacement(line, sorted, total, 0, "%p1%", "%t1%", "%c1%");
            line = applyPlacement(line, sorted, total, 1, "%p2%", "%t2%", "%c2%");
            line = applyPlacement(line, sorted, total, 2, "%p3%", "%t3%", "%c3%");

            result.add(color(line));
        }

        return result;
    }

    /**
     * Replaces placeholders for a specific leaderboard position.
     *
     * @param line current line
     * @param sorted sorted entries
     * @param total total tickets
     * @param index position index
     * @param playerPlaceholder player placeholder
     * @param ticketPlaceholder ticket placeholder
     * @param percentPlaceholder percent placeholder
     * @return updated line
     */
    private static String applyPlacement(String line,
                                         List<Map.Entry<UUID, Integer>> sorted,
                                         int total,
                                         int index,
                                         String playerPlaceholder,
                                         String ticketPlaceholder,
                                         String percentPlaceholder) {

        if (index >= sorted.size()) {
            return line.replace(playerPlaceholder, "-")
                       .replace(ticketPlaceholder, "0")
                       .replace(percentPlaceholder, "0");
        }

        Map.Entry<UUID, Integer> entry = sorted.get(index);

        String name = resolvePlayerName(entry.getKey());

        int tickets = entry.getValue();

        double percent = total == 0 ? 0 : (tickets * 100.0 / total);

        return line.replace(playerPlaceholder, name)
                   .replace(ticketPlaceholder, String.valueOf(tickets))
                   .replace(percentPlaceholder, String.format("%.1f", percent));
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
        playerNameCache.clear();
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

    private static String resolvePlayerName(UUID playerId) {
        var online = Bukkit.getPlayer(playerId);
        if (online != null) {
            String liveName = online.getName();
            playerNameCache.put(playerId, liveName);
            return liveName;
        }
        return playerNameCache.computeIfAbsent(
                playerId,
                id -> Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("Unknown")
        );
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