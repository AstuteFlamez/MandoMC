package com.astuteflamez.mandomc.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.mechanics.warps.WarpConfig;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.system.planets.ilum.configs.ParkourConfig;

/**
 * Command used to reload plugin configurations at runtime.
 *
 * Reloads the main config along with all system-specific configs
 * such as warps, parkour, and items.
 */
public class ReloadCommand implements CommandExecutor {

    /**
     * Main plugin instance.
     */
    private final MandoMC plugin;

    /**
     * Creates a new reload command.
     *
     * @param plugin the plugin instance
     */
    public ReloadCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the reload command.
     *
     * Validates permissions, reloads all configurations,
     * and reports status to the sender.
     *
     * @param sender the command sender
     * @param command the command executed
     * @param label the command label
     * @param args command arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!sender.hasPermission("mmc.reload")) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.");
            return true;
        }

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Reloading plugin...");

        try {

            /*
             * Reload configs
             */
            plugin.reloadConfig();
            WarpConfig.reload();
            ParkourConfig.reload();
            ItemsConfig.reload();

            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Configs reloaded.");

        } catch (Exception e) {

            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Reload failed. Check console.");

            e.printStackTrace();
            return true;
        }

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Reload complete.");

        return true;
    }
}