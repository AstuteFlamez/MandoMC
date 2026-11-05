package net.mandomc.gameplay.lightsaber.listener;

import net.mandomc.core.LangManager;
import net.mandomc.gameplay.lightsaber.SaberItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces disabled-state behavior while saber stamina cooldown is active.
 */
public final class SaberDisabledStateListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSaberAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!SaberItemUtil.isSaberShield(item)) return;
        if (!player.hasCooldown(Material.SHIELD)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSaberBlockAttempt(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!SaberItemUtil.isSaberShield(item)) return;
        if (!player.hasCooldown(Material.SHIELD)) return;

        event.setCancelled(true);
        player.clearActiveItem();
        player.sendMessage(LangManager.get("lightsabers.stamina-disabled"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveWhileDisabled(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.hasCooldown(Material.SHIELD)) return;
        if (!player.isBlocking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!SaberItemUtil.isSaberShield(item)) return;

        player.clearActiveItem();
    }
}
