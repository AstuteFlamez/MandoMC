package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.commands.*;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.system.EventModule;
import net.mandomc.core.modules.system.planets.ParkourModule;
import net.mandomc.mechanics.gambling.lottery.LotteryCommand;
import net.mandomc.mechanics.warps.WarpCommand;
import net.mandomc.system.discord.DiscordCommand;
import net.mandomc.system.events.commands.EventCommand;
import net.mandomc.system.events.types.jabba_dungeon.KeyCommand;
import net.mandomc.system.items.commands.*;
import net.mandomc.system.planets.ilum.commands.ParkourFinishCommand;

// ✅ ADD
import net.mandomc.system.shops.ShopCommand;

public class CommandModule implements Module {

    private final MandoMC plugin;

    public CommandModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        // =========================
        // CORE
        // =========================
        safe("warps", new WarpCommand(GUIModule.GUI_MANAGER));
        safe("test", new TestCommand());
        safe("mmcreload", new ReloadCommand(plugin));
        safe("lottery", new LotteryCommand(GUIModule.GUI_MANAGER));

        // =========================
        // SHOPS ✅ NEW
        // =========================
        safe("shop", new ShopCommand(GUIModule.GUI_MANAGER));

        // =========================
        // DISCORD
        // =========================
        safe("discord", new DiscordCommand());

        // =========================
        // LINK SYSTEM
        // =========================
        safe("link", new LinkCommand(plugin));

        // =========================
        // ITEMS
        // =========================
        GetCommand get = new GetCommand();
        GiveCommand give = new GiveCommand();
        DropCommand drop = new DropCommand();
        RecipeCommand recipe = new RecipeCommand();

        safe("get", get, get);
        safe("give", give, give);
        safe("drop", drop, drop);
        safe("recipes", recipe, recipe);

        // =========================
        // PARKOUR
        // =========================
        safe("parkourfinish", new ParkourFinishCommand(ParkourModule.PARKOUR_MANAGER));

        // =========================
        // EVENTS
        // =========================
        EventCommand eventCmd = new EventCommand(EventModule.EVENT_MANAGER);
        safe("event", eventCmd, eventCmd);

        // =========================
        // DUNGEON KEYS
        // =========================
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