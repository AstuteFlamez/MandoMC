package net.mandomc.server.items.command;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.RecipeRegistry;
import net.mandomc.server.items.gui.RecipeBrowserGUI;
import net.mandomc.server.items.gui.RecipeViewerGUI;
import net.mandomc.server.items.config.RecipeCategoryConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /recipes command.
 *
 * Opens recipe GUIs or displays specific item recipes.
 */
public class RecipeCommand implements CommandExecutor, TabCompleter {

    private final GUIManager guiManager;

    public RecipeCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    /**
     * Executes the recipes command.
     *
     * /recipes → opens main GUI
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
            guiManager.openGUI(RecipeBrowserGUI.root(guiManager), player);
            return true;
        }

        String input = args[0].toLowerCase();

        // Item recipe
        if (!RecipeRegistry.hasRecipe(input)) {
            player.sendMessage(LangManager.get("items.no-recipe", "%id%", input));
            return true;
        }

        guiManager.openGUI(
                RecipeViewerGUI.of(guiManager, input, () -> RecipeBrowserGUI.root(guiManager)),
                player
        );
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

        for (String itemId : RecipeCategoryConfig.getOrderedRecipeIds()) {
            if (itemId.startsWith(input) && RecipeRegistry.hasRecipe(itemId)) {
                completions.add(itemId);
            }
        }

        completions.addAll(
                ItemRegistry.getItemIds().stream()
                        .filter(RecipeRegistry::hasRecipe)
                        .filter(id -> id.startsWith(input))
                        .filter(id -> !completions.contains(id))
                        .collect(Collectors.toList())
        );

        return completions;
    }
}