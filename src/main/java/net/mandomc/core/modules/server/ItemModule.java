package net.mandomc.core.modules.server;

import net.mandomc.MandoMC;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.server.items.listener.ItemBrowserListener;
import net.mandomc.server.items.listener.RecipeListener;

/**
 * Manages the lifecycle of the custom item system.
 *
 * Loads all item definitions and registers item-related listeners on enable.
 * Listeners are tracked for clean unregistration on disable.
 */
public class ItemModule implements Module {

    private final MandoMC plugin;
    private ListenerRegistrar listenerRegistrar;

    /**
     * Creates the item module.
     *
     * @param plugin the plugin instance
     */
    public ItemModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the item system.
     *
     * Loads all items from config and registers item event listeners.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);
        listenerRegistrar.register(new ItemBrowserListener());
        listenerRegistrar.register(new RecipeListener());
    }

    /**
     * Disables the item system and unregisters all listeners.
     */
    @Override
    public void disable() {
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
    }
}
