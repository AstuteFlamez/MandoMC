package net.mandomc.gameplay.abilities.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Persists/cleans ability runtime state on session end.
 */
public class AbilityPersistenceListener implements Listener {
    private final AbilityService abilityService;

    public AbilityPersistenceListener(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abilityService.flushIfDirty();
        abilityService.clearCooldowns(event.getPlayer().getUniqueId());
    }
}
