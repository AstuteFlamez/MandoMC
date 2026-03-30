package net.mandomc.core.modules.core;

import java.io.File;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.VehicleRegistry;
import net.mandomc.content.vehicles.config.VehicleConfig;
import net.mandomc.core.LangManager;
import net.mandomc.core.module.Module;
import net.mandomc.mechanics.gambling.lottery.LotteryConfig;
import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;
import net.mandomc.system.shops.ShopLoader;

/**
 * Handles loading and initialization of all plugin configuration files.
 *
 * Load order is critical: item configs must be loaded before item
 * registry population, and the item registry must be populated
 * before shops are loaded.
 */
public class ConfigModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the config module.
     *
     * @param plugin the plugin instance
     */
    public ConfigModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes and loads all configuration files in dependency order.
     */
    @Override
    public void enable() {
        plugin.getDataFolder().mkdirs();

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveDefaultConfig();

        LangManager.load();

        WarpConfig.setup();
        WarpConfig.get().options().copyDefaults(true);
        WarpConfig.save();

        ParkourConfig.setup();
        ParkourConfig.get().options().copyDefaults(true);
        ParkourConfig.save();

        LotteryConfig.load();

        ItemsConfig.setup();
        VehicleConfig.setup();

        ItemsConfig.reload();
        VehicleConfig.reload();
        VehicleRegistry.load();

        ItemRegistry.clear();
        ItemLoader.loadItems();

        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        File pluginJar = null;
        try {
            pluginJar = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            plugin.getLogger().warning("[Shops] Could not resolve plugin jar path — default configs may not be copied.");
        }
        ShopLoader.loadAll(shopsFolder, pluginJar);

        plugin.getLogger().info("All configs loaded successfully.");
    }

    @Override
    public void disable() {}
}