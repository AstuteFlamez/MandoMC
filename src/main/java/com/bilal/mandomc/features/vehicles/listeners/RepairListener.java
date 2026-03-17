package com.bilal.mandomc.features.vehicles.listeners;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.items.ItemUtils;
import com.bilal.mandomc.features.vehicles.Vehicle;
import com.bilal.mandomc.features.vehicles.VehicleData;
import com.bilal.mandomc.features.vehicles.managers.VehicleRepairTransferManager;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class RepairListener implements Listener {

    @EventHandler
    public void onInteract(BaseEntityInteractEvent event) {

        if (event.getAction() != Action.INTERACT) return;

        ActiveModel clicked = event.getModel();
        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        if (VehicleRepairTransferManager.isTransferring(player)) return;

        ItemStack wrench = player.getInventory().getItemInMainHand();

        if (wrench == null) return;
        if (!ItemUtils.isItem(wrench, "wrench")) return;

        for (Vehicle vehicle : MandoMC.activeVehicles.values()) {

            VehicleData vehicleData = vehicle.getVehicleData();
            ActiveModel activeModel = vehicleData.getActiveModel();

            if (clicked != activeModel) continue;

            ItemStack vehicleItem = vehicleData.getItem();
            if (vehicleItem == null) return;

            VehicleRepairTransferManager.startTransfer(player, wrench, vehicleItem);

            return;
        }
    }
}