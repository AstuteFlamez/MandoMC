package com.bilal.mandomc.features.events;

import com.bilal.mandomc.MandoMC;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;

public class EventScheduler {

    private final MandoMC plugin;
    private final EventManager manager;

    private BukkitTask task;
    private int lastHandledMinute = -1;

    public EventScheduler(MandoMC plugin, EventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        stop();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LocalDateTime now = LocalDateTime.now();
            int minute = now.getMinute();
            int second = now.getSecond();

            if (second != 0) return;
            if (minute == lastHandledMinute) return;

            lastHandledMinute = minute;

            if (minute == 50) {
                manager.tickSchedulerPhase(EventPhase.END_WARNING);
            } else if (minute == 55) {
                manager.tickSchedulerPhase(EventPhase.FORCE_END);
            } else if (minute == 0) {
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
}