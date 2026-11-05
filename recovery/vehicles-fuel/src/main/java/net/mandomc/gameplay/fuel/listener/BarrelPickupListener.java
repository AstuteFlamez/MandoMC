package net.mandomc.gameplay.fuel.listener;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.gameplay.fuel.manager.BarrelManager;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.ItemUtils;

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
        if (!stand.getScoreboardTags().contains(BarrelManager.BARREL_TAG)) return;

        tryPickup(event.getPlayer(), stand, event);
    }

    @EventHandler
    public void onPickupBarrelBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        ArmorStand stand = BarrelManager.findBarrelAt(event.getClickedBlock());
        if (stand == null) return;

        tryPickup(event.getPlayer(), stand, event);
    }

    private void tryPickup(Player player, ArmorStand stand, org.bukkit.event.Cancellable event) {
        if (stand == null || !stand.isValid()) return;

        if (!player.isSneaking()) return;

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand != null && ItemUtils.hasTag(hand, "FUEL")) return;

        event.setCancelled(true);

        pickupBarrel(player, stand);
    }

    public static void pickupBarrel(Player player, ArmorStand stand) {
        ItemStack barrelModel = stand.getEquipment().getHelmet();
        ItemStack barrelItem = ItemRegistry.get("rhydonium_barrel");
        if (barrelItem != null) {
            int currentFuel = FuelManager.getCurrentFuel(barrelModel);
            FuelManager.updateFuel(barrelItem, currentFuel);
            barrelItem = BarrelManager.updateItem(barrelItem);
            player.getInventory().addItem(barrelItem);
            player.sendMessage(LangManager.get(
                    "fuel.barrel.picked-up",
                    "%current%", String.valueOf(currentFuel),
                    "%max%", String.valueOf(FuelManager.getMaxFuel(barrelItem))
            ));
        }

        BarrelManager.removeHologram(stand);
        BarrelManager.removeBarrier(stand);
        stand.remove();
    }
}
