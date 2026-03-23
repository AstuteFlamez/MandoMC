package com.astuteflamez.mandomc.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.mechanics.warps.WarpConfig;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;
import com.astuteflamez.mandomc.system.planets.ilum.configs.ParkourConfig;

public class ReloadCommand implements CommandExecutor {

    private final MandoMC plugin;

    public ReloadCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

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