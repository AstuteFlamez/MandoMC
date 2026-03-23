package com.astuteflamez.mandomc.system.items.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.RecipeRegistry;
import com.astuteflamez.mandomc.system.items.guis.RecipeBrowserGUI;
import com.astuteflamez.mandomc.system.items.guis.RecipeViewerGUI;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeCommand implements CommandExecutor, TabCompleter {

    /*
     * =========================
     * COMMAND
     * =========================
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        /*
         * /recipes → open main GUI
         */
        if (args.length == 0) {
            RecipeBrowserGUI.openRoot(player);
            return true;
        }

        String input = args[0].toLowerCase();

        /*
         * OPTIONAL: CATEGORY SHORTCUTS (nice UX)
         */
        switch (input) {
            case "metals" -> {
                RecipeBrowserGUI.openMetals(player);
                return true;
            }
            case "armor" -> {
                RecipeBrowserGUI.openArmor(player);
                return true;
            }
            case "hilts" -> {
                RecipeBrowserGUI.openHilts(player);
                return true;
            }
            case "sabers" -> {
                RecipeBrowserGUI.openSabers(player);
                return true;
            }
            case "components" -> {
                RecipeBrowserGUI.openComponents(player);
                return true;
            }
            case "fuel" -> {
                RecipeBrowserGUI.openFuel(player);
                return true;
            }
            case "vehicles" -> {
                RecipeBrowserGUI.openVehicles(player);
                return true;
            }
        }

        /*
         * ITEM RECIPE
         */
        if (!RecipeRegistry.hasRecipe(input)) {
            player.sendMessage("§6§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7That item has no recipe.");
            return true;
        }

        RecipeViewerGUI.open(player, input);

        return true;
    }

    /*
     * =========================
     * TAB COMPLETE
     * =========================
     */
    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();

        List<String> completions = new ArrayList<>();

        /*
         * CATEGORY SUGGESTIONS
         */
        List<String> categories = List.of(
                "metals",
                "armor",
                "hilts",
                "sabers",
                "components",
                "fuel",
                "vehicles"
        );

        for (String cat : categories) {
            if (cat.startsWith(input)) {
                completions.add(cat);
            }
        }

        /*
         * ITEM IDS (ONLY WITH RECIPES)
         */
        completions.addAll(
                ItemRegistry.getItemIds()
                        .stream()
                        .filter(RecipeRegistry::hasRecipe) // 🔥 only valid recipes
                        .filter(id -> id.startsWith(input))
                        .collect(Collectors.toList())
        );

        return completions;
    }
}