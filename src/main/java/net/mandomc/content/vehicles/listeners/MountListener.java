package net.mandomc.content.vehicles.listeners;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.VehicleData;
import net.mandomc.content.vehicles.managers.VehicleManager;
import net.mandomc.core.modules.system.VehicleModule;
import net.mandomc.mechanics.fuel.FuelManager;
import net.mandomc.system.items.ItemUtils;

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

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {

            VehicleData data = vehicle.getVehicleData();

            if (clicked != data.getActiveModel()) continue;
            if (!uuid.equals(vehicle.getOwnerUUID())) continue;

            int fuel = FuelManager.getCurrentFuel(data.getItem());

            if (fuel <= 0) {
                player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §c⚠ This vehicle has no fuel.");
                return;
            }

            VehicleManager.mountVehicle(player, vehicle);
        }
    }
}