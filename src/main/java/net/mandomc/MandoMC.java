package net.mandomc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;

import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.CommandModule;
import net.mandomc.core.modules.core.ConfigModule;
import net.mandomc.core.modules.core.DatabaseModule;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.core.modules.core.ListenerModule;
import net.mandomc.core.modules.gameplay.AbilitiesModule;
import net.mandomc.core.modules.gameplay.BountyModule;
import net.mandomc.core.modules.gameplay.FuelModule;
import net.mandomc.core.modules.gameplay.LotteryModule;
import net.mandomc.core.modules.gameplay.WarpModule;
import net.mandomc.core.modules.server.EventModule;
import net.mandomc.core.modules.server.ItemModule;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.core.modules.world.ParkourModule;
import net.mandomc.core.modules.world.TatooineModule;
import net.mandomc.core.services.ServiceRegistry;

/**
 * Main plugin class for MandoMC.
 *
 * Owns the {@link ServiceRegistry} and orchestrates the enable/disable
 * lifecycle for every subsystem module. The registry is recreated on each
 * reload so no stale service references persist between reloads.
 */
public final class MandoMC extends JavaPlugin {

    private static MandoMC instance;

    /** The live registry; recreated on each reload cycle. */
    private ServiceRegistry registry;

    /** Ordered list of modules — loaded forward, disabled in reverse. */
    private List<Module> modules;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("[MandoMC] Starting up...");
        buildModules();
        enableAll();
        getLogger().info("[MandoMC] Enabled successfully!");
    }

    @Override
    public void onDisable() {
        disableAll();
        getLogger().info("[MandoMC] Disabled.");
    }

    /**
     * Performs a full hot-reload: disables all modules in reverse order,
     * clears the registry, rebuilds module list, then re-enables in forward order.
     *
     * Called by {@link net.mandomc.core.commands.ReloadCommand}.
     */
    public void reload() {
        getLogger().info("[MandoMC] Reloading...");
        disableAll();
        buildModules();
        enableAll();
        getLogger().info("[MandoMC] Reload complete.");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void buildModules() {
        registry = new ServiceRegistry();

        modules = new ArrayList<>(List.of(
                new ConfigModule(this),
                new DatabaseModule(this),
                new EconomyModule(this),
                new LotteryModule(this),
                new GUIModule(),
                new AbilitiesModule(this),
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
        ));
    }

    private void enableAll() {
        for (Module module : modules) {
            try {
                module.enable(registry);
            } catch (Exception e) {
                getLogger().severe("[MandoMC] Failed to enable module " + module.getName() + ": " + e.getMessage());
                throw e;
            }
        }
    }

    private void disableAll() {
        List<Module> reversed = new ArrayList<>(modules);
        Collections.reverse(reversed);
        for (Module module : reversed) {
            try {
                module.disable();
            } catch (Exception e) {
                getLogger().warning("[MandoMC] Error disabling module " + module.getName() + ": " + e.getMessage());
            }
        }
        if (registry != null) {
            registry.clear();
        }
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
