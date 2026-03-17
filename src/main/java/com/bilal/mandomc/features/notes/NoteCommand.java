package com.bilal.mandomc.features.notes;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.items.ItemRegistry;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;

import java.util.*;

public class NoteCommand implements CommandExecutor, TabCompleter {

    private final NamespacedKey NOTE_VALUE;

    private final List<Integer> values = Arrays.asList(
            25000,
            50000,
            100000
    );

    public NoteCommand(MandoMC plugin) {
        NOTE_VALUE = new NamespacedKey(plugin, "note_value");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Players only.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /note <amount>");
            return true;
        }

        int amount;

        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Invalid number.");
            return true;
        }

        if (!values.contains(amount)) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Invalid note value.");
            return true;
        }

        ItemStack note = ItemRegistry.get("money_note");

        if (note == null) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Money note item missing.");
            return true;
        }

        ItemMeta meta = note.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(NOTE_VALUE, PersistentDataType.INTEGER, amount);

        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        lore.add("");
        lore.add(ChatColor.GRAY + "Value:");
        lore.add(ChatColor.GREEN + "$" + String.format("%,d", amount));

        meta.setLore(lore);
        meta.setDisplayName("§a$" + String.format("%,d", amount) + " Money Note");

        note.setItemMeta(meta);

        player.getInventory().addItem(note);

        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Given $" + String.format("%,d", amount) + " money note.");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {

            List<String> list = new ArrayList<>();

            for (Integer value : values)
                list.add(String.valueOf(value));

            return list;
        }

        return Collections.emptyList();
    }
}