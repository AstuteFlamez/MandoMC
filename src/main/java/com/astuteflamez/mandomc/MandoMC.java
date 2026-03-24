package com.astuteflamez.mandomc;

import org.bukkit.plugin.java.JavaPlugin;

import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.modules.core.CommandModule;
import com.astuteflamez.mandomc.modules.core.ConfigModule;
import com.astuteflamez.mandomc.modules.core.GUIModule;
import com.astuteflamez.mandomc.modules.core.ListenerModule;
import com.astuteflamez.mandomc.modules.mechanics.FuelModule;
import com.astuteflamez.mandomc.modules.mechanics.WarpModule;
import com.astuteflamez.mandomc.modules.system.EventModule;
import com.astuteflamez.mandomc.modules.system.ItemModule;
import com.astuteflamez.mandomc.modules.system.VehicleModule;
import com.astuteflamez.mandomc.modules.system.planets.ParkourModule;
import com.astuteflamez.mandomc.modules.system.planets.TatooineModule;

import java.util.List;

public final class MandoMC extends JavaPlugin {

    public static MandoMC instance;

    private List<Module> modules;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("§a[MandoMC] Starting up...");

        modules = List.of(
                new ConfigModule(this),
                new GUIModule(),
                new FuelModule(this),
                new WarpModule(this),
                new ParkourModule(this),
                new VehicleModule(this),
                new ItemModule(this),
                new EventModule(this),
                new CommandModule(this),
                new ListenerModule(this),
                new TatooineModule(this)
        );

        modules.forEach(Module::enable);

        getLogger().info("§a[MandoMC] Enabled successfully!");
    }

    @Override
    public void onDisable() {
        modules.forEach(Module::disable);
        getLogger().info("§c[MandoMC] Disabled.");
    }

    public static MandoMC getInstance() {
        return instance;
    }
}