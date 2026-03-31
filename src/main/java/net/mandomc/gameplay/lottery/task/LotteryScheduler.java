package net.mandomc.gameplay.lottery.task;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import net.mandomc.gameplay.lottery.LotteryHologramManager;
import net.mandomc.gameplay.lottery.LotteryTopHologramManager;
import net.mandomc.gameplay.lottery.LotteryManager;

/**
 * Handles scheduling of the lottery system.
 *
 * Responsible for:
 * - Running the weekly lottery draw
 * - Updating holograms periodically
 * - Providing formatted time and countdown utilities
 */
public class LotteryScheduler {

    private static LocalDateTime lastRun = null;
    private static net.mandomc.gameplay.lottery.config.LotteryConfig lotteryConfig;
    private static BukkitTask drawTask;

    /**
     * Starts the scheduler task.
     *
     * Runs every minute to:
     * - Update holograms
     * - Check if it's time to execute the draw
     */
    public static void start(net.mandomc.gameplay.lottery.config.LotteryConfig config, Plugin plugin) {
        stop();
        lotteryConfig = config;

        drawTask = new BukkitRunnable() {
            @Override
            public void run() {

                // Update holograms every cycle
                LotteryHologramManager.update();
                LotteryTopHologramManager.update();

                ConfigurationSection cfg = getDrawConfig();
                if (cfg == null) return;

                LocalDateTime now = LocalDateTime.now();

                if (!isDrawTime(now, cfg)) {
                    return;
                }

                if (alreadyRan(now, cfg)) {
                    return;
                }

                lastRun = now;
                LotteryManager.executeDraw();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60);
    }

    /**
     * Stops the active scheduler task, if present.
     */
    public static void stop() {
        if (drawTask != null && !drawTask.isCancelled()) {
            drawTask.cancel();
        }
        drawTask = null;
    }

    /**
     * Returns the next scheduled draw time formatted as a string.
     *
     * @return formatted next draw time
     */
    public static String getFormattedNextDraw() {
        LocalDateTime next = getNextDrawTime();
        return next.getDayOfWeek() + " " +
                String.format("%02d:%02d", next.getHour(), next.getMinute());
    }

    /**
     * Returns the remaining time until the next draw.
     *
     * @return formatted duration string
     */
    public static String getTimeRemaining() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = getNextDrawTime();

        Duration duration = Duration.between(now, next);

        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        return days + "d " + hours + "h " + minutes + "m";
    }

    /**
     * Determines whether the current time matches the configured draw time.
     */
    private static boolean isDrawTime(LocalDateTime now, ConfigurationSection cfg) {

        DayOfWeek day = DayOfWeek.valueOf(cfg.getString("day"));
        int hour = cfg.getInt("hour");
        int minute = cfg.getInt("minute");

        return now.getDayOfWeek() == day &&
               now.getHour() == hour &&
               now.getMinute() == minute;
    }

    /**
     * Prevents the draw from executing multiple times within the same minute.
     */
    private static boolean alreadyRan(LocalDateTime now, ConfigurationSection cfg) {

        if (lastRun == null) return false;

        DayOfWeek day = DayOfWeek.valueOf(cfg.getString("day"));
        int hour = cfg.getInt("hour");
        int minute = cfg.getInt("minute");

        return lastRun.getDayOfWeek() == day &&
               lastRun.getHour() == hour &&
               lastRun.getMinute() == minute;
    }

    /**
     * Computes the next draw time based on config.
     */
    private static LocalDateTime getNextDrawTime() {

        ConfigurationSection cfg = getDrawConfig();
        if (cfg == null) return LocalDateTime.now();

        DayOfWeek day = DayOfWeek.valueOf(cfg.getString("day"));
        int hour = cfg.getInt("hour");
        int minute = cfg.getInt("minute");

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime next = now
                .with(TemporalAdjusters.nextOrSame(day))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0);

        if (now.isAfter(next)) {
            next = next.plusWeeks(1);
        }

        return next;
    }

    /**
     * Retrieves the draw configuration section.
     */
    private static ConfigurationSection getDrawConfig() {
        return lotteryConfig != null ? lotteryConfig.getSection("lottery.draw") : null;
    }
}