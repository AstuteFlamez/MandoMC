package net.mandomc.mechanics.bounties;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import net.mandomc.core.modules.core.EconomyModule;

/**
 * Listens for player deaths to resolve bounty payouts.
 *
 * When a player with an active bounty is killed, the full bounty total
 * is deposited to the killer and the bounty is removed.
 */
public class BountyListener implements Listener {

    /**
     * Handles player death and pays out any active bounty to the killer.
     *
     * @param event the player death event
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        Bounty bounty = BountyStorage.get(victim.getUniqueId());
        if (bounty == null) return;

        double total = bounty.getTotal();
        EconomyModule.deposit(killer, total);
        BountyStorage.remove(victim.getUniqueId());
        BountyStorage.save();
        BountyShowcaseManager.update();
    }

    /**
     * Saves latest tracking information when a bounty target disconnects.
     *
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        Bounty bounty = BountyStorage.get(player.getUniqueId());
        if (bounty == null) {
            return;
        }

        bounty.updateTracking(player.getLocation(), System.currentTimeMillis());
        BountyStorage.save();
        BountyShowcaseManager.update();
    }

    /**
     * Ensures joining players receive the current showcase NPC state.
     *
     * @param event join event
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        BountyShowcaseManager.showTo(event.getPlayer());
    }

    /**
     * Re-syncs the showcase NPC when a player changes worlds.
     *
     * @param event world change event
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        BountyShowcaseManager.refreshViewer(event.getPlayer());
    }

    /**
     * Re-syncs the showcase NPC after respawn when the client entity list resets.
     *
     * @param event respawn event
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(net.mandomc.MandoMC.getInstance(), () -> BountyShowcaseManager.refreshViewer(player), 1L);
    }
}
