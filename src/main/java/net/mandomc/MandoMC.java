package net.mandomc;

import org.bukkit.plugin.java.JavaPlugin;

import net.mandomc.core.module.Module;
import net.mandomc.modules.core.CommandModule;
import net.mandomc.modules.core.ConfigModule;
import net.mandomc.modules.core.GUIModule;
import net.mandomc.modules.core.ListenerModule;
import net.mandomc.modules.mechanics.FuelModule;
import net.mandomc.modules.mechanics.WarpModule;
import net.mandomc.modules.system.EventModule;
import net.mandomc.modules.system.ItemModule;
import net.mandomc.modules.system.VehicleModule;
import net.mandomc.modules.system.planets.ParkourModule;
import net.mandomc.modules.system.planets.TatooineModule;

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