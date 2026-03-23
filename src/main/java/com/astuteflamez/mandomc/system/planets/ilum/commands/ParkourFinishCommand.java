package com.astuteflamez.mandomc.system.planets.ilum.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourManager;

public class ParkourFinishCommand implements CommandExecutor {

    private final ParkourManager parkourManager;

    public ParkourFinishCommand(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        /*
         * Allow console OR OP players
         */
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7This command can only be run by the server.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /parkourfinish <player>");
            return true;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Player not found.");
            return true;
        }

        parkourManager.finishParkour(player);

        return true;
    }
}