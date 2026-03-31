package net.mandomc.server.events.types.beskar;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.model.GameEvent;

public class BeskarMiningListener implements Listener {

    private final EventManager eventManager;

    public BeskarMiningListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        if (event.getBlock().getType() != Material.ANCIENT_DEBRIS) return;

        GameEvent active = eventManager.getActiveEvent();
        if (!(active instanceof BeskarRushEvent beskarEvent)) return;
        if (!beskarEvent.isRunning()) return;

        BeskarRushActiveTask task = beskarEvent.getActiveTask();
        if (task == null) return;

        BeskarOre ore = task.getOre(event.getBlock().getLocation());
        if (ore == null) return;

        event.setDropItems(false);

        ore.mine(event.getPlayer());
    }
}