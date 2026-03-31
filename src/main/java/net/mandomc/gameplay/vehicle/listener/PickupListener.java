package net.mandomc.gameplay.vehicle.listener;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.server.items.ItemUtils;

/**
 * Handles vehicle pickup when a player sneaks and interacts with their vehicle.
 *
 * Also auto-picks up vehicles when a player leaves or teleports.
 */
public class PickupListener implements Listener {

    /**
     * Handles sneak-interact to pick up a vehicle.
     *
     * Ignores interactions where the player holds a canister or wrench.
     *
     * @param event the model interaction event
     */
    @EventHandler
    public void onPickup(BaseEntityInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

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

    /**
     * Picks up the player's vehicle when they disconnect.
     *
     * @param event the player quit event
     */
    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        VehicleManager.pickupVehicle(event.getPlayer());
    }

    /**
     * Picks up the player's vehicle when they teleport.
     *
     * @param event the player teleport event
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        VehicleManager.pickupVehicle(event.getPlayer());
    }
}
