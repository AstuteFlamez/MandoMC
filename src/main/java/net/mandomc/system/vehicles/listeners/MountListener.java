package net.mandomc.system.vehicles.listeners;

import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;

import net.mandomc.mechanics.fuel.FuelManager;
import net.mandomc.modules.system.VehicleModule;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.vehicles.Vehicle;
import net.mandomc.system.vehicles.VehicleData;
import net.mandomc.system.vehicles.VehicleRegistry;
import net.mandomc.system.vehicles.config.VehiclesConfig;
import net.mandomc.system.vehicles.managers.VehicleManager;
import net.mandomc.system.vehicles.movement.AerialMountController;
import net.mandomc.system.vehicles.movement.SurfaceMountController;

import org.bukkit.configuration.file.FileConfiguration;
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

        // Prevent mounting when holding utility items
        if (hand != null && (ItemUtils.isItem(hand, "rhydonium_canister")
                || ItemUtils.isItem(hand, "wrench"))) {
            return;
        }

        ActiveModel clicked = event.getModel();
        UUID uuid = player.getUniqueId();

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {

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

            /* =========================
               MOVEMENT (NEW SYSTEM)
            ========================= */

            MountControllerType controller;

            String itemId = vehicle.getItemId();

            if (itemId != null) {

                String vehicleId = VehicleRegistry.getVehicleId(itemId);

                if (vehicleId != null) {

                    FileConfiguration config = VehiclesConfig.get(vehicleId);

                    if (config != null) {

                        String movement = config.getString("vehicle.systems.movement", "SURFACE");

                        controller = movement.equalsIgnoreCase("AERIAL")
                                ? AerialMountController.AERIAL
                                : SurfaceMountController.SURFACE;

                    } else {
                        controller = SurfaceMountController.SURFACE;
                    }

                } else {
                    controller = SurfaceMountController.SURFACE;
                }

            } else {
                controller = SurfaceMountController.SURFACE;
            }

            activeModel.getMountManager().ifPresent(mountManager -> {
                mountManager.setCanDrive(true);
                mountManager.mountDriver(player, controller);
            });

            /* =========================
               ANIMATION + SOUND
            ========================= */

            AnimationHandler handler = activeModel.getAnimationHandler();
            handler.playAnimation("mount", 0.3, 0.3, 1, true);

            VehicleManager.sound.put(uuid, vehicleData.getMovementSoundLength());

            player.playSound(
                    player.getLocation(),
                    vehicleData.getMovementSound(),
                    1f,
                    1f
            );
        }
    }
}