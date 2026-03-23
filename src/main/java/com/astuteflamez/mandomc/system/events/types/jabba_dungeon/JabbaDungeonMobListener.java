package com.astuteflamez.mandomc.system.events.types.jabba_dungeon;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.astuteflamez.mandomc.system.events.EventManager;
import com.astuteflamez.mandomc.system.events.GameEvent;

import java.util.UUID;

public class JabbaDungeonMobListener implements Listener {

    private final EventManager eventManager;

    public JabbaDungeonMobListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onMythicDeath(MythicMobDeathEvent event) {

        GameEvent active = eventManager.getActiveEvent();
        if (!(active instanceof JabbaDungeonEvent dungeon)) return;

        if (dungeon.getState() == null) return;

        JabbaDungeonState state = dungeon.getState();
        if (state.isKeyDropped()) return;

        ActiveMob mob = event.getMob();
        String mobName = mob.getType().getInternalName();
        int room = state.getCurrentRoom();

        // =========================
        // 🔥 BOSSES
        // =========================
        if (mobName.equalsIgnoreCase("Bossk") ||
            mobName.equalsIgnoreCase("BobaFett")) {

            dropKey(event.getEntity().getLocation(), room);
            return;
        }

        if (mobName.equalsIgnoreCase("Rancor")) {
            Bukkit.broadcastMessage("§aDungeon Complete!");
            dungeon.cleanupDungeon(); // 🔥 CLOSE ALL
            return;
        }

        // =========================
        // 🔥 NORMAL MOB TRACKING
        // =========================
        UUID id = event.getEntity().getUniqueId();

        boolean last = dungeon.removeMob(room, id);

        if (last) {
            dropKey(event.getEntity().getLocation(), room);
        }
    }

    private void dropKey(Location loc, int room) {

        GameEvent active = eventManager.getActiveEvent();
        if (!(active instanceof JabbaDungeonEvent dungeon)) return;

        JabbaDungeonState state = dungeon.getState();
        if (state.isKeyDropped()) return;

        state.setKeyDropped(true);

        String world = loc.getWorld().getName();

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "key drop jabba " + (room + 1) + " " +
                        loc.getX() + " " +
                        loc.getY() + " " +
                        loc.getZ() + " " +
                        world
        );

        Bukkit.broadcastMessage("§eKey dropped for Room " + room);
    }
}