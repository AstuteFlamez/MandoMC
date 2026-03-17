package com.astuteflamez.mandomc.features.items.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;
import com.astuteflamez.mandomc.features.items.guis.ItemBrowserGUI;

import java.util.List;
import java.util.stream.Collectors;

public class GiveCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.give";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /give <player> [item]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Player not found.");
            return true;
        }

        /*
         * Open GUI if item not specified
         */
        if (args.length == 1) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Console must specify an item.");
                return true;
            }

            ItemBrowserGUI.open(player, target);
            return true;
        }

        /*
         * Give specific item
         */
        String id = args[1].toLowerCase();

        ItemStack item = ItemRegistry.get(id);

        if (item == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Unknown item.");
            return true;
        }

        target.getInventory().addItem(item.clone());

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aGave §f" + id + " §ato §f" + target.getName());

        return true;
    }

    /*
     * TAB COMPLETION
     */
    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        /*
         * Player names
         */
        if (args.length == 1) {

            String input = args[0].toLowerCase();

            return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        /*
         * Item IDs
         */
        if (args.length == 2) {

            String input = args[1].toLowerCase();

            return ItemRegistry.getItemIds()
                    .stream()
                    .filter(id -> id.startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}