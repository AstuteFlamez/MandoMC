package com.bilal.mandomc.features.events.types;

import com.bilal.mandomc.features.events.AbstractGameEvent;
import com.bilal.mandomc.features.events.EventDefinition;
import com.bilal.mandomc.features.events.EventManager;
import org.bukkit.Bukkit;

public class ScavengerHuntEvent extends AbstractGameEvent {

    public ScavengerHuntEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
    }

    @Override
    protected void onStart(EventManager manager) {
        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The hunt has begun.");
    }

    @Override
    protected void onEnd(EventManager manager) {
        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The hunt is over.");
    }
}