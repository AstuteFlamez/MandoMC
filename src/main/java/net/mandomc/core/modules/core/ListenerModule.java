package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.lightsaber.listener.SaberDeflectListener;
import net.mandomc.gameplay.lightsaber.listener.SaberHitListener;
import net.mandomc.gameplay.lightsaber.listener.SaberThrowListener;
import net.mandomc.gameplay.lightsaber.listener.SaberToggleListener;
import net.mandomc.core.guis.GUIListener;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;

/**
 * Registers core event listeners shared across all subsystems.
 *
 * Includes the central GUI listener and all lightsaber interaction listeners.
 * All listeners are tracked by a {@link ListenerRegistrar} so they are cleanly
 * unregistered on disable (enabling safe /mmcreload).
 */
public class ListenerModule implements Module {

    private final MandoMC plugin;
    private ListenerRegistrar listenerRegistrar;

    /**
     * Creates the listener module.
     *
     * @param plugin the plugin instance
     */
    public ListenerModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers all core listeners with the Bukkit plugin manager.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);

        GUIManager guiManager = registry.get(GUIManager.class);

        listenerRegistrar.register(new GUIListener(guiManager));
        listenerRegistrar.register(new SaberHitListener());
        listenerRegistrar.register(new SaberThrowListener());
        listenerRegistrar.register(new SaberToggleListener());
        listenerRegistrar.register(new SaberDeflectListener());
    }

    @Override
    public void disable() {
        if (listenerRegistrar != null) {
            listenerRegistrar.unregisterAll();
        }
    }
}
