package net.mandomc;

import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.CommandModule;
import net.mandomc.core.modules.core.ConfigModule;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.core.modules.core.ListenerModule;
import net.mandomc.core.modules.mechanics.BountyModule;
import net.mandomc.core.modules.mechanics.FuelModule;
import net.mandomc.core.modules.mechanics.GamblingModule;
import net.mandomc.core.modules.mechanics.WarpModule;
import net.mandomc.core.modules.system.EventModule;
import net.mandomc.core.modules.system.ItemModule;
import net.mandomc.core.modules.system.VehicleModule;
import net.mandomc.core.modules.system.planets.ParkourModule;
import net.mandomc.core.modules.system.planets.TatooineModule;

/**
 * Main plugin class for MandoMC.
 *
 * Initializes all subsystem modules in a defined load order and delegates
 * lifecycle events (enable/disable) to each module.
 */
public final class MandoMC extends JavaPlugin {

    private static MandoMC instance;

    private List<Module> modules;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("[MandoMC] Starting up...");

        modules = List.of(
                new ConfigModule(this),
                new EconomyModule(this),
                new GamblingModule(),
                new GUIModule(),
                new BountyModule(this),
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

        getLogger().info("[MandoMC] Enabled successfully!");
    }

    @Override
    public void onDisable() {
        modules.forEach(Module::disable);
        getLogger().info("[MandoMC] Disabled.");
    }

    /**
     * Returns the singleton plugin instance.
     *
     * @return the plugin instance
     */
    public static MandoMC getInstance() {
        return instance;
    }
}