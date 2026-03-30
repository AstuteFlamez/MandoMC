package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.LangManager;

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

            sender.sendMessage(LangManager.get("lottery.no-permission"));
            return true;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("open")) {
            sender.sendMessage(LangManager.get("lottery.usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(LangManager.get("lottery.player-not-found"));
            return true;
        }

        guiManager.openGUI(new LotteryGUI(guiManager), target);
        sender.sendMessage(LangManager.get("lottery.gui-opened", "%player%", target.getName()));

        return true;
    }
}