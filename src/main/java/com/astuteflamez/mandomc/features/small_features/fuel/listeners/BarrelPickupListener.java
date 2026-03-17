package com.astuteflamez.mandomc.features.small_features.fuel.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.small_features.fuel.FuelManager;
import com.astuteflamez.mandomc.features.small_features.fuel.managers.BarrelManager;

public class BarrelPickupListener implements Listener {

    @EventHandler
    public void onPickupBarrel(PlayerInteractAtEntityEvent event) {

        Entity entity = event.getRightClicked();

        if (!(entity instanceof ArmorStand stand)) return;

        if (!stand.getScoreboardTags().contains("rhydonium_barrel")) return;

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        ItemStack hand = player.getInventory().getItemInMainHand();

        /* --------------------------------
           Hand must NOT be fuel
        -------------------------------- */

        if (hand != null && ItemUtils.hasTag(hand, "FUEL")) return;

        event.setCancelled(true);

        /* --------------------------------
           Get barrel item
        -------------------------------- */

        ItemStack barrelModel = stand.getEquipment().getHelmet();
        ItemStack barrelItem = ItemRegistry.get("rhydonium_barrel");
        int currentFuel = FuelManager.getCurrentFuel(barrelModel);
        FuelManager.updateFuel(barrelItem, currentFuel);
        barrelItem = BarrelManager.updateModel(barrelItem);

        if (barrelItem != null) {
            player.getInventory().addItem(barrelItem);
        }

        /* --------------------------------
           Remove barrel entity
        -------------------------------- */

        BarrelManager.removeHologram(stand);
        stand.remove();
    }
}