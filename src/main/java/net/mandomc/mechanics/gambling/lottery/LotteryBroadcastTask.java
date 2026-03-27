package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;

import net.mandomc.MandoMC;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles periodic lottery broadcast messages.
 */
public class LotteryBroadcastTask {

    /**
     * Starts the broadcast scheduler.
     */
    public static void start() {

        ConfigurationSection cfg =
                LotteryConfig.get().getConfigurationSection("lottery.broadcasts");

        if (cfg == null || !cfg.getBoolean("enabled")) return;

        int hours = cfg.getInt("interval-hours", 4);
        long interval = 20L * 60 * 60 * hours;

        new BukkitRunnable() {
            @Override
            public void run() {

                List<String> messages = cfg.getStringList("messages");
                if (messages.isEmpty()) return;

                String message = messages.get(
                        ThreadLocalRandom.current().nextInt(messages.size())
                );

                message = message
                        .replace("%pot%", String.valueOf(LotteryManager.getPot()))
                        .replace("%tickets%", String.valueOf(
                                LotteryManager.getAllTickets().values().stream().mapToInt(i -> i).sum()))
                        .replace("%time%", LotteryScheduler.getTimeRemaining());

                Bukkit.broadcastMessage(color(message));
            }
        }.runTaskTimer(MandoMC.getInstance(), interval, interval);
    }

    /**
     * Applies color formatting.
     *
     * @param text input text
     * @return formatted string
     */
    private static String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}