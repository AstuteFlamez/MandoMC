package com.astuteflamez.mandomc.modules.system;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.system.events.*;
import com.astuteflamez.mandomc.system.events.listeners.*;
import com.astuteflamez.mandomc.system.events.types.jabba_dungeon.*;
import com.astuteflamez.mandomc.system.events.types.koth.*;
import com.astuteflamez.mandomc.system.events.types.mining.*;

public class EventModule implements Module {

    public static EventManager EVENT_MANAGER;

    private final MandoMC plugin;
    private EventScheduler scheduler;

    public EventModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        EVENT_MANAGER = new EventManager(plugin);
        EVENT_MANAGER.load();

        scheduler = new EventScheduler(plugin, EVENT_MANAGER);
        scheduler.start();

        Bukkit.getPluginManager().registerEvents(new EventMenuListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new KothChestListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BeskarMiningListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new JabbaChestListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new DoorListener(plugin, EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new JabbaDungeonMobListener(EVENT_MANAGER), plugin);
    }

    @Override
    public void disable() {
        if (scheduler != null) scheduler.stop();
        KothEvent.clearRewardChest();
    }
}