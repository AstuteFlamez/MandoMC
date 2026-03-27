package net.mandomc.content.vehicles.listeners;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.managers.VehicleManager;
import net.mandomc.core.modules.system.VehicleModule;
import net.mandomc.system.items.ItemUtils;

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
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
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