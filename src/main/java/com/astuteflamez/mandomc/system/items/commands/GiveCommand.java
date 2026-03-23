package com.astuteflamez.mandomc.system.items.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.guis.ItemBrowserGUI;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /give command.
 *
 * Allows giving items to another player or opening the item
 * browser GUI targeting a player.
 */
public class GiveCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.give";

    /**
     * Executes the give command.
     *
     * /give <player> → opens GUI
     * /give <player> <item> → gives item
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

        if (args.length == 0) {
            sender.sendMessage(prefix("&7Usage: /give <player> [item]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(prefix("&7Player not found."));
            return true;
        }

        // Open GUI
        if (args.length == 1) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix("&7Console must specify an item."));
                return true;
            }

            ItemBrowserGUI.open(player, target);
            return true;
        }

        // Give item
        String id = args[1].toLowerCase();

        ItemStack item = ItemRegistry.get(id);
        if (item == null) {
            sender.sendMessage(prefix("&7Unknown item."));
            return true;
        }

        target.getInventory().addItem(item.clone());

        sender.sendMessage(prefix("&aGave &f" + id + " &ato &f" + target.getName()));
        return true;
    }

    /**
     * Handles tab completion.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        // Player names
        if (args.length == 1) {
            String input = args[0].toLowerCase();

            return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        // Item IDs
        if (args.length == 2) {
            String input = args[1].toLowerCase();

            return ItemRegistry.getItemIds()
                    .stream()
                    .filter(id -> id.startsWith(input))
                    .collect(Collectors.toList());
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