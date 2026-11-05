package net.mandomc.gameplay.vehicle.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.mandomc.gameplay.vehicle.weapon.WeaponSystem;

public class Vehicle {

    private WeaponSystem weaponSystem;
    private VehicleData vehicleData;
    private UUID owner;
    private String itemId;
    private String selectedSkinId = "";

    /** Seat definitions loaded from YAML. */
    private List<SeatConfig> seats = new ArrayList<>();

    /**
     * Maps each occupant's player UUID to the slot index of the seat they're in.
     * The owner/driver is stored here too so all rider lookups are consistent.
     */
    private final Map<UUID, Integer> occupants = new HashMap<>();

    public Vehicle(WeaponSystem weaponSystem,
                   VehicleData vehicleData,
                   UUID owner,
                   String itemId) {

        this.weaponSystem = weaponSystem;
        this.vehicleData = vehicleData;
        this.owner = owner;
        this.itemId = itemId.toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Seat helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the seat config at the given GUI slot, or null if none exists.
     */
    public SeatConfig getSeatAt(int slot) {
        for (SeatConfig seat : seats) {
            if (seat.slot() == slot) return seat;
        }
        return null;
    }

    /** Returns true if the given GUI slot is occupied by any player. */
    public boolean isOccupied(int slot) {
        return occupants.containsValue(slot);
    }

    /**
     * Returns the UUID of the player currently occupying the given slot,
     * or null if the slot is empty.
     */
    public UUID getOccupantAt(int slot) {
        for (Map.Entry<UUID, Integer> entry : occupants.entrySet()) {
            if (entry.getValue() == slot) return entry.getKey();
        }
        return null;
    }

    /**
     * Returns the slot index the given player is occupying, or -1 if not seated.
     */
    public int getOccupantSlot(UUID playerUUID) {
        return occupants.getOrDefault(playerUUID, -1);
    }

    /**
     * Returns the seat config the given player is currently occupying,
     * or null if they are not seated.
     */
    public SeatConfig getOccupantSeat(UUID playerUUID) {
        int slot = getOccupantSlot(playerUUID);
        return slot == -1 ? null : getSeatAt(slot);
    }

    /** Marks the given player as occupying the given slot. */
    public void occupy(UUID playerUUID, int slot) {
        occupants.put(playerUUID, slot);
    }

    /** Removes any seat occupancy record for the given player. */
    public void vacate(UUID playerUUID) {
        occupants.remove(playerUUID);
    }

    /** Returns an unmodifiable view of all current occupants (UUID → slot). */
    public Map<UUID, Integer> getOccupants() {
        return Collections.unmodifiableMap(occupants);
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public WeaponSystem getWeaponSystem() {
        return weaponSystem;
    }

    public VehicleData getVehicleData() {
        return vehicleData;
    }

    public UUID getOwnerUUID() {
        return owner;
    }

    public String getItemId() {
        return itemId;
    }

    public String getSelectedSkinId() {
        return selectedSkinId;
    }

    public List<SeatConfig> getSeats() {
        return seats;
    }

    public void setWeaponSystem(WeaponSystem weaponSystem) {
        this.weaponSystem = weaponSystem;
    }

    public void setVehicleData(VehicleData vehicleData) {
        this.vehicleData = vehicleData;
    }

    public void setOwnerUUID(UUID owner) {
        this.owner = owner;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId.toLowerCase();
    }

    public void setSelectedSkinId(String selectedSkinId) {
        this.selectedSkinId = selectedSkinId != null ? selectedSkinId : "";
    }

    public void setSeats(List<SeatConfig> seats) {
        this.seats = seats != null ? seats : new ArrayList<>();
    }
}