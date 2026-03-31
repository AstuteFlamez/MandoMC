package net.mandomc.core.modules.server;

import net.mandomc.MandoMC;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.EventScheduler;
import net.mandomc.server.events.config.EventConfig;
import net.mandomc.server.events.listener.EventMenuListener;
import net.mandomc.server.events.types.jabba.DoorListener;
import net.mandomc.server.events.types.jabba.JabbaChestListener;
import net.mandomc.server.events.types.jabba.JabbaDungeonMobListener;
import net.mandomc.server.events.types.koth.KothChestListener;
import net.mandomc.server.events.types.beskar.BeskarMiningListener;

/**
 * Manages the lifecycle of the game events system.
 *
 * Creates the {@link EventManager}, registers it in the {@link ServiceRegistry},
 * starts the event scheduler, and registers all event-related listeners on enable.
 * All listeners are tracked for clean unregistration on disable.
 */
public class EventModule implements Module {

    private final MandoMC plugin;
    private EventScheduler scheduler;
    private ListenerRegistrar listenerRegistrar;
    private EventManager eventManager;

    /**
     * Creates the event module.
     *
     * @param plugin the plugin instance
     */
    public EventModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the event system.
     *
     * Creates the EventManager, loads event definitions, registers it in the
     * service registry, starts the scheduler, and registers all event listeners.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);

        eventManager = new EventManager(plugin);
        eventManager.load();
        registry.register(EventManager.class, eventManager);

        EventConfig eventConfig = registry.get(EventConfig.class);
        scheduler = new EventScheduler(plugin, eventManager, eventConfig);
        scheduler.start();

        listenerRegistrar.register(new EventMenuListener(eventManager));
        listenerRegistrar.register(new KothChestListener());
        listenerRegistrar.register(new BeskarMiningListener(eventManager));
        listenerRegistrar.register(new JabbaChestListener(eventManager));
        listenerRegistrar.register(new DoorListener(plugin, eventManager));
        listenerRegistrar.register(new JabbaDungeonMobListener(eventManager));
    }

    /**
     * Disables the event system.
     *
     * Stops the scheduler, unregisters all listeners, and clears KOTH reward chests.
     */
    @Override
    public void disable() {
        if (scheduler != null) scheduler.stop();
        if (eventManager != null) eventManager.forceEndActiveEvent(false);
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
    }
}
