package net.mandomc.gameplay.bounty.task;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.bounty.config.BountyConfig;
import net.mandomc.gameplay.bounty.storage.BountyRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.mandomc.gameplay.bounty.model.Bounty;
import net.mandomc.gameplay.bounty.BountyShowcaseManager;

/**
 * Periodically snapshots the locations of all bounty targets who are online
 * so they can be tracked even if they log off.
 */
public class BountyTrackerTask {

    private BukkitTask task;

    private final MandoMC plugin;
    private final BountyRepository repository;
    private final BountyConfig config;

    /**
     * Creates the tracker task.
     *
     * @param plugin     the plugin instance for scheduling
     * @param repository the bounty repository to read/write
     * @param config     the typed bounty config for the tracking interval
     */
    public BountyTrackerTask(MandoMC plugin, BountyRepository repository, BountyConfig config) {
        this.plugin     = plugin;
        this.repository = repository;
        this.config     = config;
    }

    /**
     * Starts the repeating tracker task.
     */
    public void start() {
        long intervalTicks = 20L * Math.max(1, config.getTrackingIntervalSeconds());

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean updated = false;
            long now = System.currentTimeMillis();

            for (Bounty bounty : repository.findAll()) {
                Player p = Bukkit.getPlayer(bounty.getTarget());

                if (p != null && p.isOnline()) {
                    bounty.updateTracking(p.getLocation(), now);
                    updated = true;
                }
            }

            if (updated) {
                repository.touch();
                repository.flushSoon(40L);
                BountyShowcaseManager.update();
            }
        }, 0L, intervalTicks);
    }

    /**
     * Cancels the tracker task. Called on module disable.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
