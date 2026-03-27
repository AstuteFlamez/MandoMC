package net.mandomc;

import org.bukkit.plugin.java.JavaPlugin;

import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.CommandModule;
import net.mandomc.core.modules.core.ConfigModule;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.core.modules.core.ListenerModule;
import net.mandomc.core.modules.mechanics.FuelModule;
import net.mandomc.core.modules.mechanics.GamblingModule;
import net.mandomc.core.modules.mechanics.WarpModule;
import net.mandomc.core.modules.system.EventModule;
import net.mandomc.core.modules.system.ItemModule;
import net.mandomc.core.modules.system.VehicleModule;
import net.mandomc.core.modules.system.planets.ParkourModule;
import net.mandomc.core.modules.system.planets.TatooineModule;

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
                new EconomyModule(this),
                new GamblingModule(),
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