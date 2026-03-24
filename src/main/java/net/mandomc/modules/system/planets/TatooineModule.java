package net.mandomc.modules.system.planets;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.system.planets.tatooine.TatooinePotListener;

public class TatooineModule implements Module {

    private final MandoMC plugin;
    private TatooinePotListener listener;

    public TatooineModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        listener = new TatooinePotListener();
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listener.enable();
    }

    @Override
    public void disable() {
        if (listener != null) {
            listener.disable();
        }
    }
}