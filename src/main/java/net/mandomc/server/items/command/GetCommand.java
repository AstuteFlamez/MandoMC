package net.mandomc.server.items.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.gui.ItemBrowserGUI;

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
            sender.sendMessage(LangManager.get("items.no-permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("items.players-only"));
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
            player.sendMessage(LangManager.get("items.unknown-item", "%id%", id));
            return true;
        }

        // Clone to prevent modifying registry instance
        ItemStack clone = item.clone();

        player.getInventory().addItem(clone);
        player.sendMessage(LangManager.get("items.received", "%id%", id));

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
}