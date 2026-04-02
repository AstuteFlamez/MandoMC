package net.mandomc.gameplay.abilities.task;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Repeating action bar updater for selected ability/cooldown feedback.
 */
public class AbilityActionBarTask {
    private final Plugin plugin;
    private final AbilityService abilityService;
    private BukkitTask task;

    public AbilityActionBarTask(Plugin plugin, AbilityService abilityService) {
        this.plugin = plugin;
        this.abilityService = abilityService;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                String line = abilityService.actionBarLine(player);
                player.sendActionBar(line == null ? " " : line);
            }
        }, 20L, 4L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
