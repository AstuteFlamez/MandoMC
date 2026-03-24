package net.mandomc.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;

import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.ItemRegistry;

import net.mandomc.system.vehicles.config.VehiclesConfig;
import net.mandomc.system.vehicles.VehicleRegistry;

import net.mandomc.system.planets.ilum.configs.ParkourConfig;

public class ConfigModule implements Module {

    private final MandoMC plugin;

    public ConfigModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        /* ---------------------------
           Base plugin config
        --------------------------- */
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveDefaultConfig();

        /* ---------------------------
           Warp Config
        --------------------------- */
        WarpConfig.setup();
        WarpConfig.get().options().copyDefaults(true);
        WarpConfig.save();

        /* ---------------------------
           Parkour Config
        --------------------------- */
        ParkourConfig.setup();
        ParkourConfig.get().options().copyDefaults(true);
        ParkourConfig.save();

        /* ---------------------------
           Items + Vehicles Configs
        --------------------------- */
        ItemsConfig.setup();
        VehiclesConfig.setup();

        /* ---------------------------
           Load order (CRITICAL)
        --------------------------- */

        // 1. Load configs
        ItemsConfig.reload();
        VehiclesConfig.reload();

        // 2. Build vehicle mappings
        VehicleRegistry.load();

        // 3. Rebuild ALL items (🔥 THIS WAS MISSING)
        ItemRegistry.clear();
        ItemLoader.loadItems();

        plugin.getLogger().info("All configs loaded successfully.");
    }

    @Override
    public void disable() {}
}