package com.astuteflamez.mandomc.system.items.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.guis.ItemBrowserGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /get command.
 *
 * Opens the item browser GUI or gives a specific item to the player.
 */
public class GetCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.get";

    /**
     * Executes the get command.
     *
     * /get → opens GUI
     * /get <item> → gives item
     *
     * @param sender the command sender
     * @param command the command
     * @param label command label
     * @param args arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(prefix("&cYou do not have permission."));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix("&7Only players can use this command."));
            return true;
        }

        // Open GUI
        if (args.length == 0) {
            ItemBrowserGUI.open(player);
            return true;
        }

        String id = args[0].toLowerCase();

        ItemStack item = ItemRegistry.get(id);
        if (item == null) {
            player.sendMessage(prefix("&7Unknown item."));
            return true;
        }

        // Clone to prevent modifying registry instance
        ItemStack clone = item.clone();

        player.getInventory().addItem(clone);
        player.sendMessage(prefix("&7Received item &f" + id));

        return true;
    }

    /**
     * Handles tab completion for item IDs.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        if (args.length == 1) {
            return new ArrayList<>(ItemRegistry.getItemIds());
        }

        return List.of();
    }

    /**
     * Formats a prefixed message.
     */
    private String prefix(String message) {
        return color("&4&lᴍᴀɴᴅᴏᴍᴄ &r&8» " + message);
    }

    /**
     * Applies color formatting.
     */
    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}