package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import net.mandomc.core.guis.GUIManager;

/**
 * Command for managing lottery GUI access.
 */
public class LotteryCommand implements CommandExecutor {

    private final GUIManager guiManager;

    /**
     * Creates the lottery command.
     *
     * @param guiManager GUI manager instance
     */
    public LotteryCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    /**
     * Executes the lottery command.
     *
     * @param sender command sender
     * @param cmd command
     * @param label command label
     * @param args arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof ConsoleCommandSender) &&
                !sender.hasPermission("mandomc.lottery.admin")) {

            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("open")) {
            sender.sendMessage("§cUsage: /lottery open <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        guiManager.openGUI(new LotteryGUI(guiManager), target);
        sender.sendMessage("§aOpened lottery GUI for " + target.getName());

        return true;
    }
}