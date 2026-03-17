package com.astuteflamez.mandomc.features.small_features.lightsabers.listeners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;

public class SaberToggleListener implements Listener {

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {

        Player player = event.getPlayer();

        int newSlot = event.getNewSlot();
        int prevSlot = event.getPreviousSlot();

        ItemStack newItem = player.getInventory().getItem(newSlot);
        ItemStack prevItem = player.getInventory().getItem(prevSlot);

        /* SABER TURN ON */

        if (newItem != null && newItem.getType() == Material.SHIELD && ItemUtils.hasTag(newItem, "SABER")) {

            String itemId = ItemUtils.getItemId(newItem);
            if (itemId == null) return;

            ConfigurationSection sec = ItemsConfig.getItemSection(itemId);
            if (sec == null) return;

            ConfigurationSection hilt = sec.getConfigurationSection("hilt");
            if (hilt == null) return;

            int bladeCMD = sec.getInt("custom_model_data");
            int hiltCMD = hilt.getInt("custom_model_data");

            ItemMeta meta = newItem.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) return;

            if (meta.getCustomModelData() == hiltCMD) {

                meta.setCustomModelData(bladeCMD);
                newItem.setItemMeta(meta);

                player.getInventory().setItem(newSlot, newItem);

                player.playSound(player.getLocation(), "melee.lightsaber.on", 1f, 1f);
            }
        }

        /* SABER TURN OFF */

        if (prevItem != null && prevItem.getType() == Material.SHIELD && ItemUtils.hasTag(prevItem, "SABER")) {

            String itemId = ItemUtils.getItemId(prevItem);
            if (itemId == null) return;

            ConfigurationSection sec = ItemsConfig.getItemSection(itemId);
            if (sec == null) return;

            ConfigurationSection hilt = sec.getConfigurationSection("hilt");
            if (hilt == null) return;

            int bladeCMD = sec.getInt("custom_model_data");
            int hiltCMD = hilt.getInt("custom_model_data");

            ItemMeta meta = prevItem.getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) return;

            if (meta.getCustomModelData() == bladeCMD) {

                meta.setCustomModelData(hiltCMD);
                prevItem.setItemMeta(meta);

                player.getInventory().setItem(prevSlot, prevItem);

                player.playSound(player.getLocation(), "melee.lightsaber.off", 1f, 1f);
            }
        }
    }
}