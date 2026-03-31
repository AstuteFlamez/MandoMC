package net.mandomc.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;

/**
 * Command used to hot-reload the plugin at runtime.
 *
 * Delegates to {@link MandoMC#reload()} which disables all modules in reverse
 * order, clears the service registry, then re-enables them in forward order.
 * This ensures clean listener unregistration and fresh service instances every
 * reload cycle.
 */
public class ReloadCommand implements CommandExecutor {

    private final MandoMC plugin;

    /**
     * Creates the reload command.
     *
     * @param plugin the plugin instance
     */
    public ReloadCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the full module reload sequence.
     *
     * @param sender  the command sender
     * @param command the command
     * @param label   the command alias used
     * @param args    the command arguments
     * @return true always
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mmc.reload")) {
            sender.sendMessage(LangManager.get("core.reload.no-permission"));
            return true;
        }

        sender.sendMessage(LangManager.get("core.reload.reloading"));

        try {
            plugin.reload();
            sender.sendMessage(LangManager.get("core.reload.success"));
        } catch (Exception e) {
            sender.sendMessage(LangManager.get("core.reload.failure"));
            plugin.getLogger().severe("[Reload] Failed: " + e.getMessage());
        }

        return true;
    }
}
