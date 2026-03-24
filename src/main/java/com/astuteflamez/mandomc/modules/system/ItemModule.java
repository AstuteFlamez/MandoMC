package com.astuteflamez.mandomc.modules.system;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.system.items.ItemLoader;
import com.astuteflamez.mandomc.system.items.listeners.*;

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