package net.mandomc.gameplay.vehicle.listener;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.manager.SeatManager;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.core.modules.server.VehicleModule;

/**
 * Handles automatic vehicle cleanup on player disconnect and teleportation.
 *
 * Manual pickup is handled exclusively through {@link net.mandomc.gameplay.vehicle.gui.VehicleInteractGUI}
 * so that players are always prompted through the seat-selection GUI before
 * the vehicle can be reclaimed.
 */
public class PickupListener implements Listener {

    /**
     * Ejects all riders and picks up the vehicle when the owner disconnects.
     *
     * @param event the player quit event
     */
    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        autoPickup(event.getPlayer());
    }

    /**
     * Ejects all riders and picks up the vehicle when the owner teleports.
     *
     * @param event the player teleport event
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        autoPickup(event.getPlayer());
    }

    /**
     * Cleans up and returns the vehicle to the player's inventory.
     *
     * Ejects any other riders before invoking the standard pickup flow.
     */
    private void autoPickup(Player player) {
        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        SeatManager.ejectAll(vehicle);
        VehicleManager.pickupVehicle(player);
    }
}

