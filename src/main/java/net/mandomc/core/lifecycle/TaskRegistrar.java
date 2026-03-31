package net.mandomc.core.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks all scheduled tasks started by a single module so they can be
 * cancelled cleanly when the module is disabled.
 *
 * Replace all direct {@code Bukkit.getScheduler()} calls inside a module's
 * lifecycle with calls to this registrar.
 */
public final class TaskRegistrar {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    /**
     * Creates a new registrar bound to the given plugin.
     *
     * @param plugin the owning plugin
     */
    public TaskRegistrar(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules a one-shot synchronous task and tracks it.
     *
     * @param runnable the task body
     * @param delay    ticks before execution
     * @return the scheduled task
     */
    public BukkitTask runLater(Runnable runnable, long delay) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delay);
        tasks.add(task);
        return task;
    }

    /**
     * Schedules a repeating synchronous task and tracks it.
     *
     * @param runnable the task body
     * @param delay    ticks before first run
     * @param period   ticks between runs
     * @return the scheduled task
     */
    public BukkitTask runTimer(Runnable runnable, long delay, long period) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
        tasks.add(task);
        return task;
    }

    /**
     * Schedules a repeating asynchronous task and tracks it.
     *
     * @param runnable the task body
     * @param delay    ticks before first run
     * @param period   ticks between runs
     * @return the scheduled task
     */
    public BukkitTask runTimerAsync(Runnable runnable, long delay, long period) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period);
        tasks.add(task);
        return task;
    }

    /**
     * Schedules a one-shot asynchronous task and tracks it.
     *
     * @param runnable the task body
     * @return the scheduled task
     */
    public BukkitTask runAsync(Runnable runnable) {
        BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        tasks.add(task);
        return task;
    }

    /**
     * Cancels all tasks registered through this registrar.
     *
     * Safe to call multiple times; subsequent calls are no-ops if already cleared.
     */
    public void cancelAll() {
        for (BukkitTask task : tasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        tasks.clear();
    }
}
