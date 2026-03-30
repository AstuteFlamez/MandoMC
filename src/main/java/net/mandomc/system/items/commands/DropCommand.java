package net.mandomc.system.items.commands;

import net.mandomc.core.LangManager;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.system.items.ItemRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /drop command.
 *
 * Allows dropping custom items or WeaponMechanics ammo at a specified
 * location with optional world and amount parameters.
 */
public class DropCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.drop";

    /**
     * Executes the drop command.
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
            sender.sendMessage(LangManager.get("items.usage-drop"));
            return true;
        }

        String id = args[0].toLowerCase();

        Location location = parseLocation(sender, args);
        if (location == null) return true;

        int amount = parseAmount(args, sender);

        ItemStack item = resolveItem(id, amount);
        if (item == null) {
            sender.sendMessage(LangManager.get("items.unknown-item", "%id%", id));
            return true;
        }

        location.getWorld().dropItemNaturally(location, item);

        sender.sendMessage(LangManager.get("items.dropped", "%id%", id, "%amount%", String.valueOf(amount)));
        return true;
    }

    /**
     * Parses a location from command arguments.
     */
    private Location parseLocation(CommandSender sender, String[] args) {

        // Console must fully specify location
        if (!(sender instanceof Player player)) {

            if (args.length < 5) {
                sender.sendMessage("&cConsole must specify: /drop <item> <x> <y> <z> <world> [amount]");
                return null;
            }

            World world = Bukkit.getWorld(args[4]);
            if (world == null) {
                sender.sendMessage(LangManager.get("items.invalid-world"));
                return null;
            }

            Double x = parseDouble(args[1], sender);
            Double y = parseDouble(args[2], sender);
            Double z = parseDouble(args[3], sender);

            if (x == null || y == null || z == null) return null;

            return new Location(world, x, y, z);
        }

        if (args.length == 1) {
            return player.getLocation();
        }

        if (args.length >= 4) {

            Double x = parseDouble(args[1], sender);
            Double y = parseDouble(args[2], sender);
            Double z = parseDouble(args[3], sender);

            if (x == null || y == null || z == null) return null;

            World world = player.getWorld();

            if (args.length >= 5) {
                world = Bukkit.getWorld(args[4]);
                if (world == null) {
                sender.sendMessage(LangManager.get("items.invalid-world"));
                return null;
                }
            }

            return new Location(world, x, y, z);
        }

        sender.sendMessage(LangManager.get("items.usage-drop"));
        return null;
    }

    /**
     * Parses item amount from arguments.
     */
    private int parseAmount(String[] args, CommandSender sender) {

        if (args.length < 6) return 1;

        try {
            return Math.max(1, Integer.parseInt(args[5]));
        } catch (NumberFormatException e) {
            sender.sendMessage(LangManager.get("items.invalid-amount", "%amount%", args[5]));
            return 1;
        }
    }

    /**
     * Resolves item from registry or WeaponMechanics ammo.
     */
    private ItemStack resolveItem(String id, int amount) {

        ItemStack item = ItemRegistry.get(id);

        if (item != null) {
            item.setAmount(amount);
            return item;
        }

        return WeaponMechanicsAPI.generateAmmo(id, false);
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

        // Items + ammo
        if (args.length == 1) {
            List<String> list = new ArrayList<>(ItemRegistry.getItemIds());

            list.addAll(List.of(
                    "Standard_Plasma_Cells",
                    "Precision_Power_Cells",
                    "Ionized_Energy_Cells",
                    "Tibanna_Gas_Cells",
                    "Proton_Torpedo"
            ));

            return list;
        }

        // Coordinates
        if (args.length >= 2 && args.length <= 4 && sender instanceof Player player) {
            Location loc = player.getLocation();
            return switch (args.length) {
                case 2 -> List.of(String.valueOf(loc.getBlockX()));
                case 3 -> List.of(String.valueOf(loc.getBlockY()));
                case 4 -> List.of(String.valueOf(loc.getBlockZ()));
                default -> List.of();
            };
        }

        // Worlds
        if (args.length == 5) {
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return worlds;
        }

        // Amount
        if (args.length == 6) {
            return List.of("1", "8", "16", "32", "64");
        }

        return List.of();
    }

    /**
     * Safely parses a double value.
     */
    private Double parseDouble(String input, CommandSender sender) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            sender.sendMessage(LangManager.get("items.invalid-amount", "%amount%", input));
            return null;
        }
    }
}