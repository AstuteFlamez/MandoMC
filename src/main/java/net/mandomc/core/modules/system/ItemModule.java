package net.mandomc.core.modules.system;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.listeners.*;

public class ItemModule implements Module {

    private final MandoMC plugin;

    public ItemModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        ItemLoader.loadItems();

        Bukkit.getPluginManager().registerEvents(new ItemBrowserListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new RecipeListener(), plugin);
    }

    @Override
    public void disable() {}
}