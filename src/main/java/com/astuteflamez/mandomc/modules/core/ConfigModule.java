package com.astuteflamez.mandomc.modules.core;

import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.mechanics.warps.WarpConfig;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.system.planets.ilum.configs.ParkourConfig;

public class ConfigModule implements Module {

    private final MandoMC plugin;

    public ConfigModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveDefaultConfig();

        WarpConfig.setup();
        WarpConfig.get().options().copyDefaults(true);
        WarpConfig.save();

        ParkourConfig.setup();
        ParkourConfig.get().options().copyDefaults(true);
        ParkourConfig.save();

        ItemsConfig.setup();
        ItemsConfig.reload();
    }

    @Override
    public void disable() {}
}