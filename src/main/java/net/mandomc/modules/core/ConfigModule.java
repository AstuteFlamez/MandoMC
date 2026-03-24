package net.mandomc.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.system.items.configs.ItemsConfig;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;

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