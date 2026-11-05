package net.mandomc.gameplay.vehicle.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

import net.mandomc.gameplay.vehicle.manager.SeatManager;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.core.modules.server.VehicleModule;

/**
 * Fires the vehicle weapon when a player swings their arm while seated in a
 * seat that permits shooting.
 *
 * DRIVER seats always can shoot. PASSENGER seats can shoot only when tagged
 * as gunner in the vehicle seat config.
 */
public class ShootListener implements Listener {

    @EventHandler
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();

        Vehicle vehicle = VehicleModule.getVehicleForPlayer(player.getUniqueId());
        if (vehicle == null) return;

        if (!SeatManager.canShootFromSeat(player, vehicle)) return;

        vehicle.getVehicleData()
                .getActiveModel()
                .getMountManager()
                .ifPresent(mountManager -> {
                    if (!mountManager.hasRiders()) return;
                    vehicle.getWeaponSystem().shoot(vehicle, player);
                });
    }
}
