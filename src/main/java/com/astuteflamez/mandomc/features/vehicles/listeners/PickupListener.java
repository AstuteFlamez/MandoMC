package com.astuteflamez.mandomc.features.vehicles.listeners;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.vehicles.Vehicle;
import com.astuteflamez.mandomc.features.vehicles.managers.VehicleManager;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PickupListener implements Listener {

    @EventHandler
    public void onPickup(BaseEntityInteractEvent event) {

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        /* --------------------------------
           Do NOT pickup if holding canister
        -------------------------------- */

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand != null && (ItemUtils.isItem(hand, "rhydonium_canister") 
                        || ItemUtils.isItem(hand, "wrench"))) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = MandoMC.activeVehicles.get(uuid);
        if (vehicle == null) return;

        ActiveModel clickedModel = event.getModel();
        ActiveModel vehicleModel = vehicle.getVehicleData().getActiveModel();

        if (vehicleModel != clickedModel) return;

        VehicleManager.pickupVehicle(player);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        VehicleManager.pickupVehicle(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        VehicleManager.pickupVehicle(event.getPlayer());
    }
}