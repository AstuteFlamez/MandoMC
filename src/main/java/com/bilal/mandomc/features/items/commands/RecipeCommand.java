package com.bilal.mandomc.features.items.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import com.bilal.mandomc.features.items.ItemRegistry;
import com.bilal.mandomc.features.items.RecipeRegistry;
import com.bilal.mandomc.features.items.guis.RecipeBrowserGUI;
import com.bilal.mandomc.features.items.guis.RecipeViewerGUI;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {

            RecipeBrowserGUI.open(player, 0);

            return true;
        }

        String id = args[0].toLowerCase();

        if (!RecipeRegistry.hasRecipe(id)) {

            player.sendMessage("§6§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7That item has no recipe.");

            return true;
        }

        RecipeViewerGUI.open(player, id);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (args.length == 1) {

            String input = args[0].toLowerCase();

            return ItemRegistry.getItemIds()
                    .stream()
                    .filter(id -> id.startsWith(input))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}