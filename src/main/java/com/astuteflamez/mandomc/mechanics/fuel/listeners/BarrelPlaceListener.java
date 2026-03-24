package com.astuteflamez.mandomc.mechanics.fuel.listeners;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.mechanics.fuel.FuelManager;
import com.astuteflamez.mandomc.mechanics.fuel.managers.BarrelManager;
import com.astuteflamez.mandomc.system.items.ItemRegistry;
import com.astuteflamez.mandomc.system.items.ItemUtils;

public class BarrelPlaceListener implements Listener {

    @EventHandler
    public void onPlaceBarrel(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemStack model = ItemRegistry.get("rhydonium_barrel_block");

        if (item == null || model == null) return;

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
           Transfer fuel from item → model
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

        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(false);
        stand.setSmall(false);
        stand.setInvulnerable(true);

        /* Tag the armor stand */

        stand.addScoreboardTag("rhydonium_barrel");

        /* --------------------------------
           Apply +5 offset + animation
        -------------------------------- */

        ItemStack placed = model.clone();

        placed = BarrelManager.applyPlacementOffset(placed); // +5 CMD
        placed = BarrelManager.updateModel(placed);           // animation (10–14)

        stand.getEquipment().setHelmet(placed);

        /* --------------------------------
           Create hologram
        -------------------------------- */

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