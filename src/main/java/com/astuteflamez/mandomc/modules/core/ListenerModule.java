package com.astuteflamez.mandomc.modules.core;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.content.lightsabers.listeners.*;
import com.astuteflamez.mandomc.core.guis.GUIListener;
import com.astuteflamez.mandomc.core.module.Module;

public class ListenerModule implements Module {

    private final MandoMC plugin;

    public ListenerModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        Bukkit.getPluginManager().registerEvents(
                new GUIListener(GUIModule.GUI_MANAGER), plugin
        );

        Bukkit.getPluginManager().registerEvents(new SaberHitListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberThrowListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberToggleListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberDeflectListener(), plugin);
    }

    @Override
    public void disable() {}
}