package net.mandomc.mechanics.bounties;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.mandomc.MandoMC;

public class BountyTrackerTask {

    public static void start() {
        long intervalTicks = 20L * Math.max(1, BountyConfig.getTrackingInterval());

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean updated = false;
                long now = System.currentTimeMillis();

                for (Bounty bounty : BountyStorage.getAll()) {
                    Player p = Bukkit.getPlayer(bounty.getTarget());

                    if (p != null && p.isOnline()) {
                        bounty.updateTracking(p.getLocation(), now);
                        updated = true;
                    }
                }

                if (updated) {
                    BountyStorage.save();
                    BountyShowcaseManager.update();
                }

            }
        }.runTaskTimer(MandoMC.getInstance(), 0, intervalTicks);
    }
}