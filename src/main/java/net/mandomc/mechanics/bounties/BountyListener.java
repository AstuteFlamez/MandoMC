package net.mandomc.mechanics.bounties;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

}
