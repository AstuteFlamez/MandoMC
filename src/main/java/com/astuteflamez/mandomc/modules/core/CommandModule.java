package com.astuteflamez.mandomc.modules.core;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.commands.*;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.mechanics.warps.WarpCommand;
import com.astuteflamez.mandomc.modules.system.EventModule;
import com.astuteflamez.mandomc.modules.system.planets.ParkourModule;
import com.astuteflamez.mandomc.system.events.commands.EventCommand;
import com.astuteflamez.mandomc.system.events.types.jabba_dungeon.KeyCommand;
import com.astuteflamez.mandomc.system.items.commands.*;
import com.astuteflamez.mandomc.system.planets.ilum.commands.ParkourFinishCommand;

public class CommandModule implements Module {

    private final MandoMC plugin;

    public CommandModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        safe("warps", new WarpCommand(GUIModule.GUI_MANAGER));
        safe("test", new TestCommand());
        safe("mmcreload", new ReloadCommand(plugin));

        GetCommand get = new GetCommand();
        GiveCommand give = new GiveCommand();
        DropCommand drop = new DropCommand();
        RecipeCommand recipe = new RecipeCommand();

        safe("get", get, get);
        safe("give", give, give);
        safe("drop", drop, drop);
        safe("recipes", recipe, recipe);

        safe("parkourfinish", new ParkourFinishCommand(ParkourModule.PARKOUR_MANAGER));

        EventCommand eventCmd = new EventCommand(EventModule.EVENT_MANAGER);
        safe("event", eventCmd, eventCmd);

        KeyCommand key = new KeyCommand(plugin);
        safe("key", key, key);
    }

    private void safe(String name, org.bukkit.command.CommandExecutor exec) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(exec);
        }
    }

    private void safe(String name,
                      org.bukkit.command.CommandExecutor exec,
                      org.bukkit.command.TabCompleter tab) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(exec);
            plugin.getCommand(name).setTabCompleter(tab);
        }
    }

    @Override
    public void disable() {}
}