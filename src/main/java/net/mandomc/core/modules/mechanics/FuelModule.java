package net.mandomc.core.modules.mechanics;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.mechanics.fuel.listeners.BarrelCanisterInteractListener;
import net.mandomc.mechanics.fuel.listeners.BarrelPickupListener;
import net.mandomc.mechanics.fuel.listeners.BarrelPlaceListener;
import net.mandomc.mechanics.fuel.listeners.CanisterModeSwitchListener;

/**
 * Manages the lifecycle of the fuel system.
 *
 * Registers all fuel-related event listeners on enable.
 */
public class FuelModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the fuel module.
     *
     * @param plugin the plugin instance
     */
    public FuelModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the fuel system by registering all fuel event listeners.
     */
    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(new BarrelPlaceListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BarrelPickupListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BarrelCanisterInteractListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new CanisterModeSwitchListener(), plugin);
    }

    /**
     * Disables the fuel system. No cleanup required.
     */
    @Override
    public void disable() {}
}
