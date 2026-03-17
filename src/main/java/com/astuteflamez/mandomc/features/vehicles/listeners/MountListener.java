package com.astuteflamez.mandomc.features.vehicles.listeners;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.small_features.fuel.FuelManager;
import com.astuteflamez.mandomc.features.vehicles.Vehicle;
import com.astuteflamez.mandomc.features.vehicles.VehicleData;
import com.astuteflamez.mandomc.features.vehicles.managers.VehicleManager;
import com.astuteflamez.mandomc.features.vehicles.movement.AerialMountController;
import com.astuteflamez.mandomc.features.vehicles.movement.SurfaceMountController;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MountListener implements Listener {

    @EventHandler
    public void onInteract(BaseEntityInteractEvent event) {

        if (event.getAction() != Action.INTERACT) return;

        Player player = event.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand != null && (ItemUtils.isItem(hand, "rhydonium_canister")
                || ItemUtils.isItem(hand, "wrench"))) {
            return;
        }

        ActiveModel clicked = event.getModel();
        UUID uuid = player.getUniqueId();

        for (Vehicle vehicle : MandoMC.activeVehicles.values()) {

            VehicleData vehicleData = vehicle.getVehicleData();
            ActiveModel activeModel = vehicleData.getActiveModel();

            if (clicked != activeModel) continue;
            if (!uuid.equals(vehicle.getOwnerUUID())) continue;

            ItemStack vehicleItem = vehicleData.getItem();

            int fuel = FuelManager.getCurrentFuel(vehicleItem);

            if (fuel <= 0) {
                player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §c⚠ This vehicle has no fuel.");
                return;
            }

            vehicleData.getEntity().setAI(true);
            vehicleData.getEntity().setGravity(false);

            boolean aerial = ItemUtils.hasTag(vehicleItem, "AERIAL");

            MountControllerType controller =
                    aerial ? AerialMountController.AERIAL : SurfaceMountController.SURFACE;

            activeModel.getMountManager().ifPresent(mountManager -> {
                mountManager.setCanDrive(true);
                mountManager.mountDriver(player, controller);
            });

            AnimationHandler handler = activeModel.getAnimationHandler();
            handler.playAnimation("mount", 0.3, 0.3, 1, true);

            VehicleManager.sound.put(uuid, vehicleData.getMovementSoundLength());
            player.playSound(player.getLocation(), vehicleData.getMovementSound(), 1f, 1f);
        }
    }
}