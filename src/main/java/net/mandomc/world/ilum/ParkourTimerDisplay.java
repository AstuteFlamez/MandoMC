package net.mandomc.world.ilum;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.core.LangManager;
import net.mandomc.world.ilum.manager.ParkourManager;
import net.mandomc.world.ilum.manager.ParkourTimeManager;
import net.mandomc.world.ilum.model.ParkourSession;
import net.mandomc.world.ilum.util.TimeFormatter;

public class ParkourTimerDisplay {

    private final Plugin plugin;
    private final ParkourManager parkourManager;
    private final ParkourTimeManager timeManager;
    private BukkitTask timerTask;

    public ParkourTimerDisplay(
            Plugin plugin,
            ParkourManager parkourManager,
            ParkourTimeManager timeManager
    ) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
        this.timeManager = timeManager;
    }

    public void start() {
        stop();

        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : parkourManager.getActiveSessionPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) continue;

                ParkourSession session = parkourManager.getSession(player);
                if (session == null) continue;

                double currentSeconds =
                        (System.currentTimeMillis() - session.getStartTime()) / 1000.0;

                Double bestTime =
                        timeManager.getBestTime(player.getUniqueId());

                String currentRaw = TimeFormatter.format(currentSeconds);
                String bestRaw = bestTime == null
                        ? "--"
                        : TimeFormatter.format(bestTime);

                String timeColor = bestTime != null && currentSeconds < bestTime ? "&a" : "&e";
                String bestColor = bestTime == null ? "&7" : "&6";
                String message = LangManager.get(
                        "parkour.timer-actionbar",
                        "%time-color%", timeColor,
                        "%current%", timeColor + currentRaw,
                        "%best%", bestColor + bestRaw
                );
                message = LangManager.colorize(message);

                player.sendActionBar(message);

            }

        }, 20L, 2L);

    }

    public void stop() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
        }
        timerTask = null;
    }
}