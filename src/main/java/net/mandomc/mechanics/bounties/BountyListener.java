package net.mandomc.mechanics.bounties;

import net.mandomc.core.modules.core.EconomyModule;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;

public class BountyListener implements Listener {

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {

        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        Bounty bounty = BountyStorage.get(victim.getUniqueId());
        if (bounty == null) return;

        double total = bounty.getTotal();

        EconomyModule.deposit(killer, total);

        BountyStorage.remove(victim.getUniqueId());
    }
}