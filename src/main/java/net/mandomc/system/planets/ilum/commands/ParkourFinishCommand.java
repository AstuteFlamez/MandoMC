package net.mandomc.system.planets.ilum.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.mandomc.core.LangManager;
import net.mandomc.system.planets.ilum.managers.ParkourManager;

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
            sender.sendMessage(LangManager.get("parkour.server-only"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(LangManager.get("parkour.usage-finish"));
            return true;
        }

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage(LangManager.get("parkour.player-not-found"));
            return true;
        }

        parkourManager.finishParkour(player);

        return true;
    }
}