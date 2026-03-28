package net.mandomc.system.discord;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;

/**
 * Opens the Discord dialog.
 */
public class DiscordCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Dialog dialog = DiscordDialogFactory.create(player);
        player.showDialog(dialog);

        return true;
    }
}