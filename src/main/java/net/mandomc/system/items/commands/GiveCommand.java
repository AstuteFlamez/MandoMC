package net.mandomc.system.items.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.guis.ItemBrowserGUI;

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
            sender.sendMessage(LangManager.get("items.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(LangManager.get("items.usage-give"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(LangManager.get("items.player-not-found"));
            return true;
        }

        // Open GUI
        if (args.length == 1) {

            if (!(sender instanceof Player player)) {
                sender.sendMessage(LangManager.get("items.console-specify-item"));
                return true;
            }

            ItemBrowserGUI.open(player, target);
            return true;
        }

        // Give item
        String id = args[1].toLowerCase();

        ItemStack item = ItemRegistry.get(id);
        if (item == null) {
            sender.sendMessage(LangManager.get("items.unknown-item", "%id%", id));
            return true;
        }

        target.getInventory().addItem(item.clone());

        sender.sendMessage(LangManager.get("items.gave", "%id%", id, "%player%", target.getName()));
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
}