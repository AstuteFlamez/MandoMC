package net.mandomc.system.vehicles.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

import net.mandomc.modules.system.VehicleModule;
import net.mandomc.system.vehicles.Vehicle;

public class ShootListener implements Listener {

    @EventHandler
    public void onArmSwing(PlayerAnimationEvent event) {
        // Only trigger on main hand swings (left click)
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        
        // Check if the player is currently driving/using a vehicle
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(player.getUniqueId());

        if (vehicle == null) {
            return;
        }

        // Drill down into the ModelEngine / MythicMobs mount logic
        vehicle.getVehicleData()
                .getActiveModel()
                .getMountManager()
                .ifPresent(mountManager -> {

                    // Ensure the player is actually the one riding, not just nearby
                    if (!mountManager.hasRiders()) {
                        return;
                    }

                    // Execute the shoot logic
                    vehicle.getWeaponSystem().shoot(vehicle);
                });
    }
}