package net.mandomc.core.modules.core;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

import net.mandomc.MandoMC;
import net.mandomc.core.commands.LinkCommand;
import net.mandomc.core.commands.ReloadCommand;
import net.mandomc.core.commands.TestCommand;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.lottery.command.LotteryCommand;
import net.mandomc.gameplay.warp.command.WarpCommand;
import net.mandomc.server.discord.command.DiscordCommand;
import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.command.EventCommand;
import net.mandomc.server.events.types.jabba.KeyCommand;
import net.mandomc.server.items.command.DropCommand;
import net.mandomc.server.items.command.GetCommand;
import net.mandomc.server.items.command.GiveCommand;
import net.mandomc.server.items.command.RecipeCommand;
import net.mandomc.world.ilum.command.ParkourFinishCommand;
import net.mandomc.world.ilum.manager.ParkourManager;
import net.mandomc.server.shop.command.ShopCommand;
import net.mandomc.gameplay.warp.config.WarpConfig;
import net.mandomc.gameplay.lottery.config.LotteryConfig;

/**
 * Registers all plugin commands with their executors and tab completers.
 *
 * Commands are registered safely — no action is taken if a command is
 * not declared in plugin.yml. Services ({@link GUIManager}, {@link EventManager},
 * {@link ParkourManager}) are resolved from the {@link ServiceRegistry} rather
 * than accessed via static fields.
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
     * Registers all commands, resolving required services from the registry.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        GUIManager guiManager = registry.get(GUIManager.class);
        EventManager eventManager = registry.get(EventManager.class);
        ParkourManager parkourManager = registry.get(ParkourManager.class);

        WarpConfig warpConfig = registry.get(WarpConfig.class);
        safe("warps", new WarpCommand(guiManager, warpConfig));
        safe("test", new TestCommand());
        safe("mmcreload", new ReloadCommand(plugin));
        LotteryConfig lotteryConfig = registry.get(LotteryConfig.class);
        safe("lottery", new LotteryCommand(guiManager, lotteryConfig));
        ShopCommand shopCmd = new ShopCommand(guiManager);
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

        safe("parkourfinish", new ParkourFinishCommand(parkourManager));

        EventCommand eventCmd = new EventCommand(eventManager);
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