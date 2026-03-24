package com.astuteflamez.mandomc.modules.mechanics;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.mechanics.fuel.listeners.*;

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