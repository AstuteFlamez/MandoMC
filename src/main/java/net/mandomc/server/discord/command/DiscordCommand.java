package net.mandomc.server.discord.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;
import net.mandomc.core.LangManager;
import net.mandomc.server.discord.gui.DiscordDialogFactory;

/**
 * Opens the Discord dialog for the executing player.
 */
public class DiscordCommand implements CommandExecutor {

    /**
     * Opens the Discord information dialog for the player.
     *
     * @param sender the command sender
     * @param command the executed command
     * @param label the command label
     * @param args command arguments
     * @return true always
     */
    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("discord.players-only"));
            return true;
        }

        Dialog dialog = DiscordDialogFactory.create(player);
        player.showDialog(dialog);

        return true;
    }
}