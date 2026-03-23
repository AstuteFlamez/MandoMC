package com.astuteflamez.mandomc.system.items.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.RecipeRegistry;
import com.astuteflamez.mandomc.system.items.guis.RecipeBrowserGUI;
import com.astuteflamez.mandomc.system.items.guis.RecipeViewerGUI;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the /recipes command.
 *
 * Opens recipe GUIs or displays specific item recipes.
 * Supports category shortcuts and tab completion.
 */
public class RecipeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CATEGORIES = List.of(
            "metals",
            "armor",
            "hilts",
            "sabers",
            "components",
            "fuel",
            "vehicles"
    );

    /**
     * Executes the recipes command.
     *
     * /recipes → opens main GUI
     * /recipes <category> → opens category
     * /recipes <item> → opens recipe viewer
     *
     * @param sender the command sender
     * @param command the command
     * @param label command label
     * @param args arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        // Open root GUI
        if (args.length == 0) {
            RecipeBrowserGUI.openRoot(player);
            return true;
        }

        String input = args[0].toLowerCase();

        // Category handling
        if (handleCategory(player, input)) return true;

        // Item recipe
        if (!RecipeRegistry.hasRecipe(input)) {
            player.sendMessage(prefix("&7That item has no recipe."));
            return true;
        }

        RecipeViewerGUI.open(player, input);
        return true;
    }

    /**
     * Handles category shortcuts.
     *
     * @return true if a category was handled
     */
    private boolean handleCategory(Player player, String input) {

        switch (input) {
            case "metals" -> RecipeBrowserGUI.openMetals(player);
            case "armor" -> RecipeBrowserGUI.openArmor(player);
            case "hilts" -> RecipeBrowserGUI.openHilts(player);
            case "sabers" -> RecipeBrowserGUI.openSabers(player);
            case "components" -> RecipeBrowserGUI.openComponents(player);
            case "fuel" -> RecipeBrowserGUI.openFuel(player);
            case "vehicles" -> RecipeBrowserGUI.openVehicles(player);
            default -> {
                return false;
            }
        }

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

        if (args.length != 1) return List.of();

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();

        // Categories
        for (String category : CATEGORIES) {
            if (category.startsWith(input)) {
                completions.add(category);
            }
        }

        // Items with recipes
        completions.addAll(
                ItemRegistry.getItemIds()
                        .stream()
                        .filter(RecipeRegistry::hasRecipe)
                        .filter(id -> id.startsWith(input))
                        .collect(Collectors.toList())
        );

        return completions;
    }

    /**
     * Formats a prefixed message.
     */
    private String prefix(String message) {
        return color("&6&lᴍᴀɴᴅᴏᴍᴄ &r&8» " + message);
    }

    /**
     * Applies color formatting.
     */
    private String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}