package net.mandomc.core.module;

import net.mandomc.core.services.ServiceRegistry;

/**
 * Represents a self-contained plugin subsystem.
 *
 * Each module is responsible for initializing and cleaning up
 * its own resources (listeners, commands, tasks, etc.).
 *
 * Modules receive the shared {@link ServiceRegistry} in {@link #enable(ServiceRegistry)}
 * to register the services they produce and resolve the services they consume.
 * Load order determines which services are already present when a module enables.
 */
public interface Module {

    /**
     * Initializes and activates this module.
     *
     * Called once during plugin startup and again after each full reload.
     * Modules must register any services they expose into {@code registry}.
     *
     * @param registry the shared service registry
     */
    void enable(ServiceRegistry registry);

    /**
     * Deactivates and cleans up this module.
     *
     * Called once during plugin shutdown and before each full reload.
     * Modules must cancel tasks, unregister listeners, and unregister
     * any services they added to the registry.
     */
    void disable();

    /**
     * Returns a human-readable name used in log messages.
     *
     * @return the module name
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
