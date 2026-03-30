package net.mandomc.mechanics.fuel.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;

import net.mandomc.mechanics.fuel.FuelManager;
import net.mandomc.mechanics.fuel.managers.BarrelManager;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.ItemUtils;

/**
 * Listens for players picking up a placed rhydonium barrel.
 *
 * A sneaking player right-clicking a barrel armor stand (without holding a fuel item)
 * transfers the barrel's current fuel into a portable barrel item and removes the stand.
 */
public class BarrelPickupListener implements Listener {

    /**
     * Handles right-click interaction with a barrel armor stand.
     *
     * Cancels the event and gives the player a barrel item with matching fuel level,
     * then removes the armor stand and its hologram.
     *
     * @param event the entity interact event
     */
    @EventHandler
    public void onPickupBarrel(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();

        if (!(entity instanceof ArmorStand stand)) return;
        if (!stand.getScoreboardTags().contains("rhydonium_barrel")) return;

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand != null && ItemUtils.hasTag(hand, "FUEL")) return;

        event.setCancelled(true);

        ItemStack barrelModel = stand.getEquipment().getHelmet();
        ItemStack barrelItem = ItemRegistry.get("rhydonium_barrel");
        int currentFuel = FuelManager.getCurrentFuel(barrelModel);
        FuelManager.updateFuel(barrelItem, currentFuel);
        barrelItem = BarrelManager.updateItem(barrelItem);

        if (barrelItem != null) {
            player.getInventory().addItem(barrelItem);
        }

        BarrelManager.removeHologram(stand);
        stand.remove();
    }
}
