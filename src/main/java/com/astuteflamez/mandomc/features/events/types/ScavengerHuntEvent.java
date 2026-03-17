package com.astuteflamez.mandomc.features.events.types;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.features.events.AbstractGameEvent;
import com.astuteflamez.mandomc.features.events.EventDefinition;
import com.astuteflamez.mandomc.features.events.EventManager;

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