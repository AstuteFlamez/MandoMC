package net.mandomc.core.modules.gameplay;

import net.mandomc.MandoMC;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.fuel.listener.BarrelCanisterInteractListener;
import net.mandomc.gameplay.fuel.listener.BarrelPickupListener;
import net.mandomc.gameplay.fuel.listener.BarrelPlaceListener;
import net.mandomc.gameplay.fuel.listener.CanisterModeSwitchListener;

/**
 * Manages the lifecycle of the fuel system.
 *
 * Registers all fuel-related event listeners on enable. Listeners are tracked
 * for clean unregistration on disable.
 */
public class FuelModule implements Module {

    private final MandoMC plugin;
    private ListenerRegistrar listenerRegistrar;

    /**
     * Creates the fuel module.
     *
     * @param plugin the plugin instance
     */
    public FuelModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the fuel system by registering all fuel event listeners.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);
        listenerRegistrar.register(new BarrelPlaceListener());
        listenerRegistrar.register(new BarrelPickupListener());
        listenerRegistrar.register(new BarrelCanisterInteractListener());
        listenerRegistrar.register(new CanisterModeSwitchListener());
    }

    /**
     * Disables the fuel system and unregisters all listeners.
     */
    @Override
    public void disable() {
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
    }
}
