package net.mandomc.gameplay.vehicle.listener;

import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.vehicle.manager.SeatManager;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.SeatType;
import net.mandomc.gameplay.vehicle.model.Vehicle;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.UUID;

/**
 * Handles passenger dismount on sneak input.
 *
 * Drivers keep using ModelEngine mount-controller logic; this listener only
 * handles PASSENGER seats so any sneak attempt dismounts immediately.
 */
public class PassengerSneakDismountListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Vehicle vehicle = VehicleModule.getVehicleForPlayer(uuid);
        if (vehicle == null) return;

        SeatConfig seat = vehicle.getOccupantSeat(uuid);
        if (seat == null || seat.type() != SeatType.PASSENGER) return;

        SeatManager.dismountSeat(player, vehicle);
    }
}
