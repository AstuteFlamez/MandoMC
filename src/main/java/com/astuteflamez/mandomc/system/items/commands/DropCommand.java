package com.astuteflamez.mandomc.system.items.commands;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.system.items.ItemRegistry;

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
            sender.sendMessage("§cUsage: /drop <item> [x y z] [world] [amount]");
            return true;
        }

        String id = args[0];
        int amount = 1;

        Location loc;

        // =========================
        // 📍 LOCATION PARSING
        // =========================

        if (!(sender instanceof Player player)) {

            if (args.length < 5) {
                sender.sendMessage("§cConsole must specify: /drop <item> <x> <y> <z> <world> [amount]");
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

            // amount (optional)
            if (args.length >= 6) {
                amount = parseAmount(args[5], sender);
            }

        } else {

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

                // amount (optional)
                if (args.length >= 6) {
                    amount = parseAmount(args[5], sender);
                }

            } else {
                player.sendMessage("§cUsage: /drop <item> [x y z] [world] [amount]");
                return true;
            }
        }

        // =========================
        // 🎯 ITEM / AMMO RESOLUTION
        // =========================

        ItemStack item = ItemRegistry.get(id.toLowerCase());

        // If not found in ItemRegistry → try WeaponMechanics ammo
        if (item == null) {
            item = WeaponMechanicsAPI.generateAmmo(id, false);
        } else {
            item.setAmount(amount);
        }

        if (item == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Unknown item or ammo.");
            return true;
        }

        // =========================
        // 🪂 DROP
        // =========================

        loc.getWorld().dropItemNaturally(loc, item);

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Dropped §f" + id + " §7x" + amount);

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

    private int parseAmount(String input, CommandSender sender) {
        try {
            int value = Integer.parseInt(input);
            return Math.max(1, value);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount: " + input);
            throw e;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        // Item IDs + Ammo types
        if (args.length == 1) {
            List<String> list = new ArrayList<>(ItemRegistry.getItemIds());

            list.add("Standard_Plasma_Cells");
            list.add("Precision_Power_Cells");
            list.add("Ionized_Energy_Cells");
            list.add("Tibanna_Gas_Cells");
            list.add("Proton_Torpedo");

            return list;
        }

        // Coordinates
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

        // World
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
}