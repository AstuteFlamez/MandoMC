package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.VehicleRegistry;
import net.mandomc.content.vehicles.config.VehicleConfig;
import net.mandomc.core.module.Module;

import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.mechanics.gambling.lottery.LotteryConfig;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;

// ✅ ADD
import net.mandomc.system.shops.ShopLoader;

import java.io.File;

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
        plugin.getDataFolder().mkdirs(); // ✅ ensure folder exists

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
           Lottery Config
        --------------------------- */
        LotteryConfig.load();

        /* ---------------------------
           Items + Vehicles Configs
        --------------------------- */
        ItemsConfig.setup();
        VehicleConfig.setup();

        /* ---------------------------
           Load order (CRITICAL)
        --------------------------- */

        // 1. Load configs
        ItemsConfig.reload();
        VehicleConfig.reload();

        // 2. Vehicles
        VehicleRegistry.load();

        // 3. Items
        ItemRegistry.clear();
        ItemLoader.loadItems();

        /* ---------------------------
           SHOPS (AFTER ITEMS) ✅
        --------------------------- */
        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        ShopLoader.loadAll(shopsFolder);

        plugin.getLogger().info("All configs loaded successfully (including shops).");
    }

    @Override
    public void disable() {}
}