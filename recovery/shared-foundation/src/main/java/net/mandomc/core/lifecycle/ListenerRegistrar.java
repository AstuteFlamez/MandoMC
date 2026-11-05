package net.mandomc.core.lifecycle;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Tracks all event listeners registered by a single module so they can
 * be cleanly unregistered when the module is disabled.
 *
 * Replace all direct {@code Bukkit.getPluginManager().registerEvents()} calls
 * inside a module with calls to this registrar.
 */
public final class ListenerRegistrar {

    private final Plugin plugin;
    private final List<Listener> registered = new ArrayList<>();

    /**
     * Creates a new registrar bound to the given plugin.
     *
     * @param plugin the owning plugin
     */
    public ListenerRegistrar(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the listener and tracks it for later cleanup.
     *
     * @param listener the listener to register
     */
    public void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registered.add(listener);
    }

    /**
     * Unregisters all listeners that were registered through this registrar.
     *
     * Safe to call multiple times; subsequent calls are no-ops if already cleared.
     */
    public void unregisterAll() {
        for (Listener listener : registered) {
            HandlerList.unregisterAll(listener);
        }
        registered.clear();
    }
}
