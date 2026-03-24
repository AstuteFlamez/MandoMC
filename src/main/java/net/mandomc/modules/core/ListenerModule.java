package net.mandomc.modules.core;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.content.lightsabers.listeners.*;
import net.mandomc.core.guis.GUIListener;
import net.mandomc.core.module.Module;

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