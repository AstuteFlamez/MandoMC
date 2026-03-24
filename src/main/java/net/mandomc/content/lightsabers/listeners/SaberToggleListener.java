package net.mandomc.content.lightsabers.listeners;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.config.ItemsConfig;

/**
 * Handles automatic lightsaber activation and deactivation
 * when switching hotbar slots.
 *
 * Turning on occurs when selecting a saber.
 * Turning off occurs when deselecting a saber.
 */
public class SaberToggleListener implements Listener {

    /**
     * Handles hotbar slot switching.
     *
     * Activates the newly selected saber and deactivates
     * the previously held saber.
     *
     * @param event the item held event
     */
    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {

        Player player = event.getPlayer();

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());

        // Turn on newly selected saber
        toggleSaber(player, newItem, true, event.getNewSlot());

        // Turn off previously held saber
        toggleSaber(player, prevItem, false, event.getPreviousSlot());
    }

    /**
     * Toggles a saber between hilt and blade state.
     *
     * @param player the player
     * @param item the item to toggle
     * @param enable true to activate blade, false to deactivate
     * @param slot the inventory slot
     */
    private void toggleSaber(Player player, ItemStack item, boolean enable, int slot) {

        if (!isSaber(item)) return;

        String itemId = ItemUtils.getItemId(item);
        ConfigurationSection section = getItemSection(itemId);
        if (section == null) return;

        ConfigurationSection hilt = section.getConfigurationSection("hilt");
        if (hilt == null) return;

        int bladeCMD = section.getInt("custom_model_data");
        int hiltCMD = hilt.getInt("custom_model_data");

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;

        int currentCMD = meta.getCustomModelData();

        if (enable && currentCMD == hiltCMD) {
            meta.setCustomModelData(bladeCMD);
            applyMeta(player, item, slot, meta, "melee.lightsaber.on");
        }

        if (!enable && currentCMD == bladeCMD) {
            meta.setCustomModelData(hiltCMD);
            applyMeta(player, item, slot, meta, "melee.lightsaber.off");
        }
    }

    /**
     * Applies updated item meta and plays sound.
     */
    private void applyMeta(Player player, ItemStack item, int slot, ItemMeta meta, String sound) {

        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);

        player.playSound(player.getLocation(), sound, 1f, 1f);
    }

    /**
     * Checks whether an item is a valid lightsaber.
     */
    private boolean isSaber(ItemStack item) {
        return item != null
                && item.getType() == Material.SHIELD
                && ItemUtils.hasTag(item, "SABER");
    }

    /**
     * Retrieves item configuration section.
     */
    private ConfigurationSection getItemSection(String itemId) {
        return itemId != null ? ItemsConfig.getItemSection(itemId) : null;
    }
}