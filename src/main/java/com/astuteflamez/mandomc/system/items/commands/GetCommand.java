package com.astuteflamez.mandomc.system.items.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.guis.ItemBrowserGUI;

import java.util.ArrayList;
import java.util.List;

public class GetCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.get";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.You don't have permission.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            ItemBrowserGUI.open(player);
            return true;
        }

        String id = args[0].toLowerCase();

        ItemStack item = ItemRegistry.get(id);

        if (item == null) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Unknown item.");
            return true;
        }

        player.getInventory().addItem(item);
        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Received item §f" + id);

        return true;
    }

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
}