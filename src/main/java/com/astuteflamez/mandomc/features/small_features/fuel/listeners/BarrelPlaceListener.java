package com.astuteflamez.mandomc.features.small_features.fuel.listeners;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.small_features.fuel.FuelManager;
import com.astuteflamez.mandomc.features.small_features.fuel.managers.BarrelManager;

public class BarrelPlaceListener implements Listener {

    @EventHandler
    public void onPlaceBarrel(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemStack model = ItemRegistry.get("rhydonium_barrel_block");

        if (item == null) return;

        /* --------------------------------
           Check fuel tag
        -------------------------------- */

        if (!ItemUtils.hasTag(item, "FUEL")) return;

        /* --------------------------------
           Only allow barrel placement
        -------------------------------- */

        if (!ItemUtils.isItem(item, "rhydonium_barrel")) return;

        event.setCancelled(true);

        /* --------------------------------
           Update fuel
        -------------------------------- */

        int currentFuel = FuelManager.getCurrentFuel(item);
        FuelManager.updateFuel(model, currentFuel);

        /* --------------------------------
           Spawn barrel armor stand
        -------------------------------- */

        Location placeLoc = event.getClickedBlock()
                .getLocation()
                .add(0.5, 1, 0.5);

        ArmorStand stand = placeLoc.getWorld().spawn(placeLoc, ArmorStand.class);

        stand.setInvisible(false);
        stand.setGravity(false);
        stand.setMarker(false);
        stand.setSmall(false);
        stand.setInvulnerable(true);

        /* Tag the armor stand so we know it's a barrel */

        stand.addScoreboardTag("rhydonium_barrel");

        /* Put barrel item model on head */

        stand.getEquipment().setHelmet(model.clone());

        ArmorStand holo = BarrelManager.createHologram(stand);
        stand.addPassenger(holo);

        /* --------------------------------
           Consume item
        -------------------------------- */

        int amount = item.getAmount();

        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }
    }
}