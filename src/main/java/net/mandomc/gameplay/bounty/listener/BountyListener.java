package net.mandomc.gameplay.bounty.listener;

import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.gameplay.bounty.storage.BountyRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.mandomc.gameplay.bounty.BountyShowcaseManager;

public class BountyListener implements Listener {

    private final BountyRepository repository;

    public BountyListener(BountyRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        repository.findById(victim.getUniqueId()).ifPresent(bounty -> {
            double total = bounty.getTotal();
            EconomyModule.deposit(killer, total);
            repository.delete(victim.getUniqueId());
            repository.flush();
            BountyShowcaseManager.update();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        repository.findById(player.getUniqueId()).ifPresent(bounty -> {
            bounty.updateTracking(player.getLocation(), System.currentTimeMillis());
            repository.flush();
            BountyShowcaseManager.update();
        });
    }
}
