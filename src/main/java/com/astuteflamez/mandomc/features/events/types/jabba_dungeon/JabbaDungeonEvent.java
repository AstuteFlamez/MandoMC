package com.astuteflamez.mandomc.features.events.types.jabba_dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import com.astuteflamez.mandomc.features.events.AbstractGameEvent;
import com.astuteflamez.mandomc.features.events.EventDefinition;
import com.astuteflamez.mandomc.features.events.EventManager;

import java.util.ArrayList;
import java.util.List;

public class JabbaDungeonEvent extends AbstractGameEvent {

    private final EventDefinition definition;
    private final List<Location> placedChests = new ArrayList<>();

    public JabbaDungeonEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
        this.definition = definition;
    }

    @Override
    protected void onStart(EventManager manager) {

        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The dungeon has opened! Enter if you dare.");

        // open first door
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "opendoor 1");

        // 🔥 PLACE CHESTS
        Object raw = definition.getSetting("spawn-points");

        if (!(raw instanceof List<?> list)) return;

        List<String> points = list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();

        if (points != null) {
            for (String s : points) {

                String[] split = s.split(",");

                String world = split[0];
                int x = Integer.parseInt(split[1]);
                int y = Integer.parseInt(split[2]);
                int z = Integer.parseInt(split[3]);

                Location loc = new Location(
                        Bukkit.getWorld(world),
                        x, y, z
                );

                if (loc.getWorld() == null) continue;

                Block block = loc.getBlock();
                block.setType(Material.CHEST);

                placedChests.add(loc);
            }
        }
    }

    @Override
    protected void onEnd(EventManager manager) {

        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The dungeon has closed.");

        // 🔥 REMOVE CHESTS
        for (Location loc : placedChests) {
            loc.getBlock().setType(Material.AIR);
        }
        placedChests.clear();

        int[] doors = {1,5,6,13,4,7,8,11};

        for (int id : doors) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "closedoor " + id);
        }
    }
}