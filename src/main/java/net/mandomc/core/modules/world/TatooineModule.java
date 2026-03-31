package net.mandomc.core.modules.world;

import net.mandomc.MandoMC;
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
        listener = new TatooinePotListener();
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