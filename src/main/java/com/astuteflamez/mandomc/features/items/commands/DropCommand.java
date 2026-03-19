package com.astuteflamez.mandomc.features.items.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;

import java.util.ArrayList;
import java.util.List;

public class DropCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.items.drop";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /drop <item> [x y z] [world]");
            return true;
        }

        String id = args[0].toLowerCase();
        ItemStack item = ItemRegistry.get(id);

        if (item == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Unknown item.");
            return true;
        }

        Location loc;

        // 🔥 Console MUST provide full args
        if (!(sender instanceof Player player)) {

            if (args.length < 5) {
                sender.sendMessage("§cConsole must specify: /drop <item> <x> <y> <z> <world>");
                return true;
            }

            World world = Bukkit.getWorld(args[4]);
            if (world == null) {
                sender.sendMessage("§cInvalid world.");
                return true;
            }

            double x = parseDouble(args[1], sender);
            double y = parseDouble(args[2], sender);
            double z = parseDouble(args[3], sender);

            loc = new Location(world, x, y, z);

        } else {

            // Player logic
            if (args.length == 1) {
                loc = player.getLocation();
            } else if (args.length >= 4) {

                double x = parseDouble(args[1], sender);
                double y = parseDouble(args[2], sender);
                double z = parseDouble(args[3], sender);

                World world = player.getWorld();

                if (args.length >= 5) {
                    world = Bukkit.getWorld(args[4]);
                    if (world == null) {
                        player.sendMessage("§cInvalid world.");
                        return true;
                    }
                }

                loc = new Location(world, x, y, z);

            } else {
                player.sendMessage("§cUsage: /drop <item> [x y z] [world]");
                return true;
            }
        }

        // 🪂 Drop item
        loc.getWorld().dropItemNaturally(loc, item);

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Dropped §f" + id);

        return true;
    }

    private double parseDouble(String input, CommandSender sender) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + input);
            throw e;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        // Item ID
        if (args.length == 1) {
            return new ArrayList<>(ItemRegistry.getItemIds());
        }

        // Coordinates (optional suggestions)
        if (args.length >= 2 && args.length <= 4) {
            if (sender instanceof Player player) {
                Location loc = player.getLocation();
                return switch (args.length) {
                    case 2 -> List.of(String.valueOf(loc.getBlockX()));
                    case 3 -> List.of(String.valueOf(loc.getBlockY()));
                    case 4 -> List.of(String.valueOf(loc.getBlockZ()));
                    default -> List.of();
                };
            }
        }

        // World names
        if (args.length == 5) {
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return worlds;
        }

        return List.of();
    }
}