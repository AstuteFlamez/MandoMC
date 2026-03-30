package net.mandomc.core.module;

/**
 * Represents a self-contained plugin subsystem.
 *
 * Each module is responsible for initializing and cleaning up
 * its own resources (listeners, commands, tasks, etc.).
 */
public interface Module {

    /**
     * Initializes and activates this module.
     *
     * Called once during plugin startup.
     */
    void enable();

    /**
     * Deactivates and cleans up this module.
     *
     * Called once during plugin shutdown.
     */
    void disable();
}