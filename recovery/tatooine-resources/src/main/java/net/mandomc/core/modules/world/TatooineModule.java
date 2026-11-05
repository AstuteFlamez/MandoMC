package net.mandomc.core.modules.world;

import net.mandomc.MandoMC;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.world.tatooine.TatooinePotListener;

public class TatooineModule implements Module {

    private final MandoMC plugin;
    private TatooinePotListener listener;
    private ListenerRegistrar listenerRegistrar;

    public TatooineModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);
        MainConfig mainConfig = registry.get(MainConfig.class);
        if (mainConfig == null) {
            plugin.getLogger().warning("Tatooine module disabled: MainConfig is unavailable.");
            return;
        }
        listener = new TatooinePotListener(mainConfig);
        listenerRegistrar.register(listener);
        listener.enable();
    }

    @Override
    public void disable() {
        if (listener != null) {
            listener.disable();
        }
        if (listenerRegistrar != null) {
            listenerRegistrar.unregisterAll();
        }
    }
}