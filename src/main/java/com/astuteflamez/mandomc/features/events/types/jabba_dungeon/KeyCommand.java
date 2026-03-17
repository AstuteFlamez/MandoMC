package com.astuteflamez.mandomc.features.events.types.jabba_dungeon;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemRegistry;

import java.util.*;

public class KeyCommand implements CommandExecutor, TabCompleter {

    private final NamespacedKey KEY_ID;

    public KeyCommand(MandoMC plugin) {
        this.KEY_ID = new NamespacedKey(plugin, "key_id");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command. ");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /key <dungeon> <door>");
            return true;
        }

        int door;

        try {
            door = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Door must be a number.");
            return true;
        }

        Integer doorId = getDoorId(door);

        if (doorId == null) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Invalid door.");
            return true;
        }

        ItemStack key = ItemRegistry.get("keycard");

        if (key == null) {
            player.sendMessage(ChatColor.RED + "Keycard item not found.");
            return true;
        }

        ItemMeta meta = key.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // store real door ID used by the door system
        pdc.set(KEY_ID, PersistentDataType.INTEGER, doorId);

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        lore.add("");
        lore.add(ChatColor.GRAY + "Access:");
        lore.add(ChatColor.AQUA + "Door " + door); // show player-facing door number

        meta.setLore(lore);
        key.setItemMeta(meta);

        player.getInventory().addItem(key);

        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Given key for door " + door);

        return true;
    }

    private Integer getDoorId(int door) {

        switch (door) {
            case 1: return 1;
            case 2: return 5;
            case 3: return 6;
            case 4: return 13;
            case 5: return 4;
            case 6: return 7;
            case 7: return 8;
            case 8: return 11;
            default: return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return Collections.singletonList("jabba");
        }

        if (args.length == 2) {
            return Arrays.asList("1","2","3","4","5","6","7","8");
        }

        return Collections.emptyList();
    }
}