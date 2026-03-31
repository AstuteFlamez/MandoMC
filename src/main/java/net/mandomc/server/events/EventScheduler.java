package net.mandomc.server.events;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.MandoMC;
import net.mandomc.server.events.config.EventConfig;

import java.time.LocalDateTime;
import net.mandomc.server.events.model.EventPhase;

public class EventScheduler {

    private final MandoMC plugin;
    private final EventManager manager;
    private final EventConfig eventConfig;

    private BukkitTask task;
    private int lastHandledMinute = -1;

    public EventScheduler(MandoMC plugin, EventManager manager, EventConfig eventConfig) {
        this.plugin = plugin;
        this.manager = manager;
        this.eventConfig = eventConfig;
    }

    public void start() {
        stop();
        lastHandledMinute = -1;

        if (eventConfig != null && !eventConfig.isSchedulerEnabled()) {
            plugin.getLogger().info("[Events] Scheduler disabled by config.");
            return;
        }

        final int endWarningMinute = sanitizeMinute(eventConfig != null ? eventConfig.getEndWarningMinute() : 50);
        final int endMinute = sanitizeMinute(eventConfig != null ? eventConfig.getEndMinute() : 55);
        final int startWarningMinute = sanitizeMinute(eventConfig != null ? eventConfig.getStartWarningMinute() : 55);
        final int startMinute = sanitizeMinute(eventConfig != null ? eventConfig.getStartMinute() : 0);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LocalDateTime now = LocalDateTime.now();
            int minute = now.getMinute();
            int second = now.getSecond();

            if (second != 0) return;
            if (minute == lastHandledMinute) return;

            lastHandledMinute = minute;

            if (minute == endWarningMinute) {
                manager.tickSchedulerPhase(EventPhase.END_WARNING);
            }
            if (minute == endMinute) {
                manager.tickSchedulerPhase(EventPhase.FORCE_END);
            }
            if (minute == startWarningMinute) {
                manager.tickSchedulerPhase(EventPhase.START_WARNING);
            }
            if (minute == startMinute) {
                manager.tickSchedulerPhase(EventPhase.START);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private int sanitizeMinute(int configuredMinute) {
        return Math.min(59, Math.max(0, configuredMinute));
    }
}