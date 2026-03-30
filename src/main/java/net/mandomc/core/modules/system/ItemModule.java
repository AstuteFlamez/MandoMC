package net.mandomc.core.modules.system;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.listeners.ItemBrowserListener;
import net.mandomc.system.items.listeners.RecipeListener;

/**
 * Manages the lifecycle of the custom item system.
 *
 * Loads all item definitions and registers item-related listeners on enable.
 */
public class ItemModule implements Module {

    private final MandoMC plugin;

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
    public void enable() {
        ItemLoader.loadItems();

        Bukkit.getPluginManager().registerEvents(new ItemBrowserListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new RecipeListener(), plugin);
    }

    /**
     * Disables the item system. No cleanup required.
     */
    @Override
    public void disable() {}
}
