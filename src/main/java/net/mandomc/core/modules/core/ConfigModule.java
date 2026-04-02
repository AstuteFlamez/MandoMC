package net.mandomc.core.modules.core;

import java.io.File;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.config.VehicleDefinitionConfig;
import net.mandomc.core.LangManager;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.bounty.config.BountyConfig;
import net.mandomc.gameplay.abilities.config.AbilityDefinitionConfig;
import net.mandomc.gameplay.lightsaber.config.LightsaberConfig;
import net.mandomc.gameplay.lottery.config.LotteryConfig;
import net.mandomc.gameplay.warp.config.WarpConfig;
import net.mandomc.server.events.config.EventConfig;
import net.mandomc.server.items.ItemLoader;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.config.ItemDefinitionConfig;
import net.mandomc.server.items.config.ItemsConfig;
import net.mandomc.server.items.config.RecipeCategoryConfig;
import net.mandomc.world.ilum.config.ParkourConfig;
import net.mandomc.server.shop.ShopLoader;
import net.mandomc.server.shop.config.ShopConfig;

/**
 * Handles loading and initialization of all plugin configuration files.
 *
 * Instantiates all typed {@link net.mandomc.core.config.BaseConfig} subclasses
 * and registers them in the {@link ServiceRegistry} so other modules can
 * inject them via constructor rather than accessing static config classes.
 *
 * Load order is critical: item configs and vehicle configs must be loaded
 * before their respective registries are populated, and the item registry
 * must be ready before shops are loaded.
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
     * Initializes and loads all configuration files in dependency order,
     * then registers typed config objects in the service registry.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        plugin.getDataFolder().mkdirs();

        // ── Main plugin config ──────────────────────────────────────────
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveDefaultConfig();

        MainConfig mainConfig = new MainConfig(plugin);
        registry.register(MainConfig.class, mainConfig);

        LightsaberConfig lightsaberConfig = new LightsaberConfig(plugin);
        registry.register(LightsaberConfig.class, lightsaberConfig);

        LangManager.load();

        ItemsConfig.setup();
        RecipeCategoryConfig.setup();
        VehicleConfig.setup();
        ItemsConfig.reload();
        RecipeCategoryConfig.reload();
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

        // ── Typed config objects ────────────────────────────────────────
        BountyConfig bountyConfig = new BountyConfig(plugin);
        registry.register(BountyConfig.class, bountyConfig);

        LotteryConfig lotteryConfig = new LotteryConfig(plugin);
        registry.register(LotteryConfig.class, lotteryConfig);

        WarpConfig warpConfig = new WarpConfig(plugin);
        registry.register(WarpConfig.class, warpConfig);

        EventConfig eventConfig = new EventConfig(plugin);
        registry.register(EventConfig.class, eventConfig);

        ParkourConfig parkourConfig = new ParkourConfig(plugin);
        registry.register(ParkourConfig.class, parkourConfig);

        VehicleDefinitionConfig vehicleDefinitionConfig = new VehicleDefinitionConfig(plugin);
        vehicleDefinitionConfig.reload();
        registry.register(VehicleDefinitionConfig.class, vehicleDefinitionConfig);

        ItemDefinitionConfig itemDefinitionConfig = new ItemDefinitionConfig(plugin);
        itemDefinitionConfig.reload();
        registry.register(ItemDefinitionConfig.class, itemDefinitionConfig);

        ShopConfig shopConfig = new ShopConfig(plugin);
        shopConfig.reload();
        registry.register(ShopConfig.class, shopConfig);

        AbilityDefinitionConfig abilityDefinitionConfig = new AbilityDefinitionConfig(plugin);
        abilityDefinitionConfig.reload();
        registry.register(AbilityDefinitionConfig.class, abilityDefinitionConfig);

        plugin.getLogger().info("All configs loaded successfully.");
    }

    @Override
    public void disable() {}
}
