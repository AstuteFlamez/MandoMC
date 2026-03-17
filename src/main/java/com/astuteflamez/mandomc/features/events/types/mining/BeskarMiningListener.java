package com.astuteflamez.mandomc.features.events.types.mining;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import com.astuteflamez.mandomc.features.events.EventManager;
import com.astuteflamez.mandomc.features.events.GameEvent;

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