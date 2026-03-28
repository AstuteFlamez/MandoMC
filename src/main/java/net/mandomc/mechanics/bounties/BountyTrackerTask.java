package net.mandomc.mechanics.bounties;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.mandomc.MandoMC;

public class BountyTrackerTask {

    public static void start() {
        new BukkitRunnable() {
            @Override
            public void run() {

                for (Bounty bounty : BountyStorage.getAll()) {
                    Player p = Bukkit.getPlayer(bounty.getTarget());

                    if (p != null && p.isOnline()) {
                        bounty.setLastKnownLocation(p.getLocation());
                        bounty.setLastSeen(System.currentTimeMillis());
                    }
                }

            }
        }.runTaskTimer(MandoMC.getInstance(), 0, 20 * 600); // 10 minutes
    }
}