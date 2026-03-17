package com.astuteflamez.mandomc.features.notes;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;

import com.astuteflamez.mandomc.MandoMC;

public class NoteListener implements Listener {

    private final NamespacedKey NOTE_VALUE;

    public NoteListener(MandoMC plugin) {
        NOTE_VALUE = new NamespacedKey(plugin, "note_value");
    }

    @EventHandler
    public void onRedeem(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer value = pdc.get(NOTE_VALUE, PersistentDataType.INTEGER);
        if (value == null) return;

        event.setCancelled(true);

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "eco give " + player.getName() + " " + value
        );

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        String formatted = "$" + String.format("%,d", value);

        player.sendTitle(
                "§aRedeemed!",
                "§f" + formatted,
                10,   // fade in (ticks)
                60,   // stay
                20    // fade out
        );
    }
}