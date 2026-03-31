package net.mandomc.gameplay.vehicle.manager;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.mount.controller.MountControllerTypes;

import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.SeatType;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Handles seat-aware mounting and dismounting for vehicles.
 *
 * Routing:
 *  - DRIVER seats   → full movement control via existing {@link VehicleManager} flow.
 *  - PASSENGER seats → ModelEngine passenger mount; no movement control.
 *  - Shooting is controlled by seat tags (see {@link SeatConfig#canShoot()}).
 */
public class SeatManager {

    private SeatManager() {}

    // -------------------------------------------------------------------------
    // Mount
    // -------------------------------------------------------------------------

    /**
     * Mounts the given player into the specified seat of the vehicle.
     *
     * Performs the following checks before mounting:
     * <ol>
     *   <li>Fuel is available (denies with lang message on failure).</li>
     *   <li>The seat is not already occupied (denies with a message on failure).</li>
     * </ol>
     *
     * @param player  the player requesting to sit
     * @param vehicle the vehicle they are entering
     * @param seat    the seat they clicked
     */
    public static void mountSeat(Player player, Vehicle vehicle, SeatConfig seat) {
        VehicleData data = vehicle.getVehicleData();

        // Fuel check
        int fuel = FuelManager.getCurrentFuel(data.getItem());
        if (fuel <= 0) {
            player.sendMessage(LangManager.get("vehicles.no-fuel"));
            return;
        }

        // Seat occupancy check
        if (vehicle.isOccupied(seat.slot())) {
            UUID occupantId = vehicle.getOccupantAt(seat.slot());
            String name = occupantId != null ? fetchPlayerName(occupantId) : "Unknown";
            player.sendMessage(LangManager.get("vehicles.seat-occupied",
                    "%player%", name));
            return;
        }

        if (seat.type() == SeatType.DRIVER) {
            mountDriver(player, vehicle);
        } else {
            mountPassenger(player, vehicle, seat);
        }
    }

    // -------------------------------------------------------------------------
    // Dismount
    // -------------------------------------------------------------------------

    /**
     * Dismounts the given player from whatever seat they're currently in.
     *
     * If the player is not recorded as an occupant of this vehicle, this
     * method is a no-op.
     *
     * @param player  the player to dismount
     * @param vehicle the vehicle they are leaving
     */
    public static void dismountSeat(Player player, Vehicle vehicle) {
        UUID uuid     = player.getUniqueId();
        SeatConfig seat = vehicle.getOccupantSeat(uuid);
        if (seat == null) return;

        ActiveModel model = vehicle.getVehicleData().getActiveModel();

        if (seat.type() == SeatType.DRIVER) {
            VehicleManager.dismountVehicle(player, vehicle, model);
        } else {
            dismountPassenger(player, vehicle);
        }

        vehicle.vacate(uuid);
        VehicleModule.unregisterOccupant(uuid);
    }

    /**
     * Dismounts all current occupants of the vehicle.
     *
     * For riders who are offline (e.g. on server quit), the tracking data is
     * cleaned up without attempting to interact with the player entity.
     * ModelEngine cleans up naturally when the vehicle entity is subsequently destroyed.
     *
     * @param vehicle the vehicle to clear
     */
    public static void ejectAll(Vehicle vehicle) {
        Map<UUID, Integer> snapshot = new java.util.HashMap<>(vehicle.getOccupants());

        for (UUID uuid : snapshot.keySet()) {
            Player rider = Bukkit.getPlayer(uuid);
            if (rider != null && rider.isOnline()) {
                SeatConfig seat = vehicle.getOccupantSeat(uuid);
                ActiveModel model = vehicle.getVehicleData().getActiveModel();

                if (seat != null && seat.type() == SeatType.DRIVER) {
                    VehicleManager.dismountVehicle(rider, vehicle, model);
                } else {
                    dismountPassenger(rider, vehicle);
                    rider.sendMessage(LangManager.get("vehicles.ejected"));
                }
            }

            vehicle.vacate(uuid);
            VehicleModule.unregisterOccupant(uuid);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Mounts the player as the driver. Delegates to the existing
     * {@link VehicleManager#mountVehicle} flow and records occupancy.
     */
    private static void mountDriver(Player player, Vehicle vehicle) {
        VehicleManager.mountVehicle(player, vehicle);

        // Record the driver's occupancy so seat-aware lookups work
        SeatConfig driverSeat = vehicle.getSeats().stream()
                .filter(s -> s.type() == SeatType.DRIVER)
                .findFirst()
                .orElse(null);

        if (driverSeat != null) {
            vehicle.occupy(player.getUniqueId(), driverSeat.slot());
            // Owners are already in activeVehicles; no occupantIndex entry needed
        }
    }

    /**
     * Mounts the player as a passenger using ModelEngine's
     * passenger API and registers them in the tracking maps.
     */
    private static void mountPassenger(Player player, Vehicle vehicle, SeatConfig seat) {
        ActiveModel model = vehicle.getVehicleData().getActiveModel();

        model.getMountManager().ifPresent(mountManager ->
                mountManager.mountPassenger(seat.mountBone(), player, MountControllerTypes.WALKING));

        vehicle.occupy(player.getUniqueId(), seat.slot());
        VehicleModule.registerOccupant(player.getUniqueId(), vehicle.getOwnerUUID());
    }

    /** Dismounts a passenger from the ModelEngine model. */
    private static void dismountPassenger(Player player, Vehicle vehicle) {
        ActiveModel model = vehicle.getVehicleData().getActiveModel();
        model.getMountManager().ifPresent(mountManager ->
                mountManager.dismountPassenger(player));
    }

    /** Resolves a display name for the given UUID using online/offline player data. */
    private static String fetchPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    /** Returns true if this player is currently in a seat on the given vehicle. */
    public static boolean isOccupying(Player player, Vehicle vehicle) {
        return vehicle.getOccupantSlot(player.getUniqueId()) != -1;
    }

    /** Returns true if the player can shoot from their current seat. */
    public static boolean canShootFromSeat(Player player, Vehicle vehicle) {
        SeatConfig seat = vehicle.getOccupantSeat(player.getUniqueId());
        return seat != null && seat.canShoot();
    }
}
