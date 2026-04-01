package net.mandomc.gameplay.lightsaber.listener;

import net.mandomc.gameplay.lightsaber.SaberItemUtil;
import net.mandomc.gameplay.lightsaber.SaberStaminaManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Ensures SABER durability is controlled by stamina logic only.
 */
public final class SaberDurabilityGuardListener implements Listener {

    private final SaberStaminaManager staminaManager;

    public SaberDurabilityGuardListener(SaberStaminaManager staminaManager) {
        this.staminaManager = staminaManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!staminaManager.shouldBlockExternalDurability()) return;
        if (!SaberItemUtil.isSaberShield(event.getItem())) return;
        event.setCancelled(true);
    }
}
