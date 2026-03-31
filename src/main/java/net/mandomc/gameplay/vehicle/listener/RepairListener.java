package net.mandomc.gameplay.vehicle.listener;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.manager.VehicleRepairTransferManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.server.items.ItemUtils;

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

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {

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