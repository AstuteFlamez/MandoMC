package net.mandomc.core.modules.core;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import net.mandomc.MandoMC;
import net.mandomc.core.commands.LinkCommand;
import net.mandomc.core.commands.ReloadCommand;
import net.mandomc.core.commands.TestCommand;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.system.EventModule;
import net.mandomc.core.modules.system.planets.ParkourModule;
import net.mandomc.mechanics.gambling.lottery.LotteryCommand;
import net.mandomc.mechanics.warps.WarpCommand;
import net.mandomc.system.discord.DiscordCommand;
import net.mandomc.system.events.commands.EventCommand;
import net.mandomc.system.events.types.jabba_dungeon.KeyCommand;
import net.mandomc.system.items.commands.DropCommand;
import net.mandomc.system.items.commands.GetCommand;
import net.mandomc.system.items.commands.GiveCommand;
import net.mandomc.system.items.commands.RecipeCommand;
import net.mandomc.system.planets.ilum.commands.ParkourFinishCommand;
import net.mandomc.system.shops.ShopCommand;

/**
 * Registers all plugin commands with their executors and tab completers.
 *
 * Commands are registered safely — no action is taken if a command is
 * not declared in plugin.yml.
 */
public class CommandModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the command module.
     *
     * @param plugin the plugin instance
     */
    public CommandModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers all commands.
     */
    @Override
    public void enable() {
        safe("warps", new WarpCommand(GUIModule.GUI_MANAGER));
        safe("test", new TestCommand());
        safe("mmcreload", new ReloadCommand(plugin));
        safe("lottery", new LotteryCommand(GUIModule.GUI_MANAGER));
        ShopCommand shopCmd = new ShopCommand(GUIModule.GUI_MANAGER);
        safe("shop", shopCmd, shopCmd);
        safe("discord", new DiscordCommand());
        safe("link", new LinkCommand(plugin));

        GetCommand getCmd = new GetCommand();
        GiveCommand giveCmd = new GiveCommand();
        DropCommand dropCmd = new DropCommand();
        RecipeCommand recipeCmd = new RecipeCommand();

        safe("get", getCmd, getCmd);
        safe("give", giveCmd, giveCmd);
        safe("drop", dropCmd, dropCmd);
        safe("recipes", recipeCmd, recipeCmd);

        safe("parkourfinish", new ParkourFinishCommand(ParkourModule.PARKOUR_MANAGER));

        EventCommand eventCmd = new EventCommand(EventModule.EVENT_MANAGER);
        safe("event", eventCmd, eventCmd);

        KeyCommand keyCmd = new KeyCommand(plugin);
        safe("key", keyCmd, keyCmd);
    }

    @Override
    public void disable() {}

    /**
     * Safely registers a command executor.
     *
     * @param name the command name as declared in plugin.yml
     * @param exec the executor to assign
     */
    private void safe(String name, CommandExecutor exec) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(exec);
        }
    }

    /**
     * Safely registers a command executor and tab completer.
     *
     * @param name the command name as declared in plugin.yml
     * @param exec the executor to assign
     * @param tab  the tab completer to assign
     */
    private void safe(String name, CommandExecutor exec, TabCompleter tab) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(exec);
            plugin.getCommand(name).setTabCompleter(tab);
        }
    }
}