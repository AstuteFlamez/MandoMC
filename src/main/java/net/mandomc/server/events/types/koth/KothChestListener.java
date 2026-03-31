package net.mandomc.server.events.types.koth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class KothChestListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        if (!KothEvent.isRewardChest(event.getClickedBlock().getLocation())) return;

        event.setCancelled(true);
        KothEvent.claimRewardChest(event.getPlayer());
    }
}