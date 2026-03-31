package net.mandomc.gameplay.vehicle.listener;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.fuel.manager.VehicleFuelTransferManager;
import net.mandomc.server.items.ItemUtils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class VehicleCanisterInteractListener implements Listener {

    @EventHandler
    public void onInteract(BaseEntityInteractEvent event) {

        if (event.getAction() != Action.INTERACT) return;

        ActiveModel clicked = event.getModel();
        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        /* Prevent multiple vehicle transfers */

        if (VehicleFuelTransferManager.isTransferring(player)) return;

        ItemStack canister = player.getInventory().getItemInMainHand();

        if (canister == null) return;
        if (!ItemUtils.hasTag(canister, "FUEL")) return;
        if (!ItemUtils.isItem(canister, "rhydonium_canister")) return;

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {

            VehicleData vehicleData = vehicle.getVehicleData();
            ActiveModel activeModel = vehicleData.getActiveModel();

            if (clicked != activeModel) continue;

            ItemStack vehicleFuelTank = vehicleData.getItem();

            if (vehicleFuelTank == null) return;

            VehicleFuelTransferManager.startTransfer(player, canister, vehicleFuelTank);

            return;
        }
    }
}