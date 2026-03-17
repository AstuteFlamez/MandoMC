package com.astuteflamez.mandomc.features.items.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.items.guis.ItemBrowserGUI;

import java.util.UUID;

public class ItemBrowserListener implements Listener {

    private static final String TITLE = "§8Item Browser";

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (!e.getView().getTitle().equals(TITLE)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String name = clicked.getItemMeta().getDisplayName();

        Player target = player;

        if (player.hasMetadata("item_browser_target")) {

            UUID targetId = (UUID) player.getMetadata("item_browser_target").get(0).value();

            Player t = Bukkit.getPlayer(targetId);
            if (t != null) target = t;
        }

        String category = null;
        if (player.hasMetadata("item_browser_category")) {
            category = player.getMetadata("item_browser_category").get(0).asString();
        }

        int page = 0;
        if (player.hasMetadata("item_browser_page")) {
            page = player.getMetadata("item_browser_page").get(0).asInt();
        }

        /*
         * CATEGORY CLICK
         */
        if (clicked.getType() == Material.PAPER) {

            String newCategory = ChatColor.stripColor(name);

            ItemBrowserGUI.open(player, target, 0, newCategory);
            return;
        }

        /*
         * NEXT PAGE
         */
        if (name.equalsIgnoreCase("§aNext Page")) {
            ItemBrowserGUI.open(player, target, page + 1, category);
            return;
        }

        /*
         * PREVIOUS PAGE
         */
        if (name.equalsIgnoreCase("§cPrevious Page")) {
            ItemBrowserGUI.open(player, target, page - 1, category);
            return;
        }

        /*
         * GIVE ITEM
         */
        String id = ItemUtils.getItemId(clicked);

        if (id == null) return;
        if (!ItemRegistry.getItemIds().contains(id)) return;

        target.getInventory().addItem(clicked.clone());

        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aGave item to §f" + target.getName());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {

        if (e.getView().getTitle().equals(TITLE)) {
            e.setCancelled(true);
        }
    }
}