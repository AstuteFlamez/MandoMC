package net.mandomc.world.ilum;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.world.ilum.manager.ParkourManager;
import net.mandomc.world.ilum.manager.ParkourTimeManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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

            for (Player player : Bukkit.getOnlinePlayers()) {

                if (player == null) continue;

                if (!parkourManager.hasSession(player)) continue;

                ParkourSession session = parkourManager.getSession(player);

                double currentSeconds =
                        (System.currentTimeMillis() - session.getStartTime()) / 1000.0;

                Double bestTime =
                        timeManager.getBestTime(player.getUniqueId());

                String current = TimeFormatter.format(currentSeconds);
                String best = bestTime == null
                        ? "--"
                        : TimeFormatter.format(bestTime);

                String timeColor = "§e"; // default yellow

                if (bestTime != null && currentSeconds < bestTime) {
                    timeColor = "§a"; // green if beating best
                }

                String message =
                        timeColor + "Time: §f" + current +
                        " §7| §6Best: §f" + best;

                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent(message)
                );

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