package net.mandomc.core.modules.core;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.content.lightsabers.listeners.SaberDeflectListener;
import net.mandomc.content.lightsabers.listeners.SaberHitListener;
import net.mandomc.content.lightsabers.listeners.SaberThrowListener;
import net.mandomc.content.lightsabers.listeners.SaberToggleListener;
import net.mandomc.core.guis.GUIListener;
import net.mandomc.core.module.Module;

/**
 * Registers core event listeners shared across all subsystems.
 *
 * Includes the central GUI listener and all lightsaber interaction listeners.
 */
public class ListenerModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the listener module.
     *
     * @param plugin the plugin instance
     */
    public ListenerModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers all core listeners with the Bukkit plugin manager.
     */
    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(new GUIListener(GUIModule.GUI_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberHitListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberThrowListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberToggleListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new SaberDeflectListener(), plugin);
    }

    @Override
    public void disable() {}
}