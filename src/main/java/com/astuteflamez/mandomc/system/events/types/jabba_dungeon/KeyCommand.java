package com.astuteflamez.mandomc.system.events.types.jabba_dungeon;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.system.items.ItemRegistry;

import java.util.*;

public class KeyCommand implements CommandExecutor, TabCompleter {

    private final NamespacedKey KEY_ID;

    public KeyCommand(MandoMC plugin) {
        this.KEY_ID = new NamespacedKey(plugin, "key_id");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 3) {
            sender.sendMessage("§7Usage:");
            sender.sendMessage("§7/key get <dungeon> <door>");
            sender.sendMessage("§7/key give <dungeon> <door> <player>");
            sender.sendMessage("§7/key drop <dungeon> <door> <x> <y> <z> [world]");
            return true;
        }

        String sub = args[0].toLowerCase();

        int door;
        try {
            door = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cDoor must be a number.");
            return true;
        }

        Integer doorId = getDoorId(door);
        if (doorId == null) {
            sender.sendMessage("§cInvalid door.");
            return true;
        }

        ItemStack key = createKey(door, doorId);
        if (key == null) {
            sender.sendMessage(ChatColor.RED + "Keycard item not found.");
            return true;
        }

        switch (sub) {

            case "get": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can use this.");
                    return true;
                }

                player.getInventory().addItem(key);
                player.sendMessage("§7Given key for door " + door);
                break;
            }

            case "give": {
                if (args.length < 4) {
                    sender.sendMessage("§7Usage: /key give <dungeon> <door> <player>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found.");
                    return true;
                }

                target.getInventory().addItem(key);
                sender.sendMessage("§7Given key to " + target.getName());
                break;
            }

            case "drop": {

                if (args.length < 6) {
                    sender.sendMessage("§7Usage: /key drop <dungeon> <door> <x> <y> <z> [world]");
                    return true;
                }

                try {
                    double x = Double.parseDouble(args[3]);
                    double y = Double.parseDouble(args[4]);
                    double z = Double.parseDouble(args[5]);

                    World world;

                    // ✅ If sender is player → use their world
                    if (sender instanceof Player player) {
                        world = player.getWorld();
                    } else {
                        // ✅ Console must specify world
                        if (args.length < 7) {
                            sender.sendMessage("§cConsole must specify world.");
                            return true;
                        }

                        world = Bukkit.getWorld(args[6]);
                        if (world == null) {
                            sender.sendMessage("§cInvalid world.");
                            return true;
                        }
                    }

                    Location loc = new Location(world, x, y, z);

                    world.dropItemNaturally(loc, key);

                    sender.sendMessage("§7Dropped key at location.");

                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid coordinates.");
                }

                break;
            }

            default:
                sender.sendMessage("§cUnknown subcommand.");
        }

        return true;
    }

    private ItemStack createKey(int door, int doorId) {

        ItemStack key = ItemRegistry.get("keycard");
        if (key == null) return null;

        key = key.clone();

        ItemMeta meta = key.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(KEY_ID, PersistentDataType.INTEGER, doorId);

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Access:");
        lore.add(ChatColor.AQUA + "Room " + door);

        meta.setLore(lore);
        key.setItemMeta(meta);

        return key;
    }

    private Integer getDoorId(int door) {
        return switch (door) {
            case 1 -> 15;
            case 2 -> 16;
            case 3 -> 17;
            case 4 -> 18;
            case 5 -> 19;
            case 6 -> 20;
            case 7 -> 21;
            case 8 -> 22;
            case 9 -> 23;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return Arrays.asList("get", "give", "drop");
        }

        if (args.length == 2) {
            return Collections.singletonList("jabba");
        }

        if (args.length == 3) {
            return Arrays.asList("1","2","3","4","5","6","7","8","9");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return null;
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("drop")) {
            return Arrays.asList("~", "~ ~", "~ ~ ~");
        }

        return Collections.emptyList();
    }
}