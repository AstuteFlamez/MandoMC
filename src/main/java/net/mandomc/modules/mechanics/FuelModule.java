package net.mandomc.modules.mechanics;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.mechanics.fuel.listeners.*;

public class FuelModule implements Module {

    private final MandoMC plugin;

    public FuelModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        Bukkit.getPluginManager().registerEvents(new BarrelPlaceListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BarrelPickupListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BarrelCanisterInteractListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new CanisterModeSwitchListener(), plugin);
    }

    @Override
    public void disable() {}
}