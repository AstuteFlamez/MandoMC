package com.bilal.mandomc.features.events.types.jabba_dungeon;

import com.bilal.mandomc.features.events.AbstractGameEvent;
import com.bilal.mandomc.features.events.EventDefinition;
import com.bilal.mandomc.features.events.EventManager;
import org.bukkit.Bukkit;

public class JabbaDungeonEvent extends AbstractGameEvent {

    public JabbaDungeonEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
    }

    @Override
    protected void onStart(EventManager manager) {

        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The dungeon has opened! Enter if you dare.");

        // open first door
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "opendoor 1"
        );
    }

    @Override
    protected void onEnd(EventManager manager) {

        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The dungeon has closed.");

        int[] doors = {
            1,
            5,
            6,
            13,
            4,
            7,
            8,
            11
        };

        for (int id : doors) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    "closedoor " + id
            );
        }
    }
}