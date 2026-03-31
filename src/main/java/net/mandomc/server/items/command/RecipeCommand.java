package net.mandomc.server.items.command;

import net.mandomc.core.LangManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.RecipeRegistry;
import net.mandomc.server.items.gui.RecipeBrowserGUI;
import net.mandomc.server.items.gui.RecipeViewerGUI;

import java.util.ArrayList;
import java.util.List;
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
            player.sendMessage(LangManager.get("items.no-recipe", "%id%", input));
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
}