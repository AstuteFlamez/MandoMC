package net.mandomc.system.events.types.jabba_dungeon;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.mandomc.system.events.EventManager;
import net.mandomc.system.events.GameEvent;
import net.mandomc.core.LangManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listens for MythicMob deaths during the Jabba's Dungeon event.
 *
 * Tracks when the last guard in a room is killed to drop a key,
 * and ends the dungeon when the final boss (Rancor) dies.
 */
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

        if (mobName.equalsIgnoreCase("Bossk") ||
            mobName.equalsIgnoreCase("BobaFett")) {

            dropKey(event.getEntity().getLocation(), room);
            return;
        }

        if (mobName.equalsIgnoreCase("Rancor")) {
            Bukkit.broadcastMessage(LangManager.get("jabba.dungeon-complete"));
            dungeon.cleanupDungeon();
            return;
        }

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

        Bukkit.broadcastMessage(LangManager.get("jabba.key-dropped", "%room%", String.valueOf(room)));
    }
}