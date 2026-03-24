package net.mandomc.system.vehicles;

import java.util.UUID;

import net.mandomc.system.vehicles.weapons.WeaponSystem;

public class Vehicle {

    private WeaponSystem weaponSystem;
    private VehicleData vehicleData;
    private UUID owner;

    // 🔥 NEW: store item id
    private String itemId;

    public Vehicle(WeaponSystem weaponSystem,
                   VehicleData vehicleData,
                   UUID owner,
                   String itemId) {

        this.weaponSystem = weaponSystem;
        this.vehicleData = vehicleData;
        this.owner = owner;
        this.itemId = itemId.toLowerCase(); // normalize
    }

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
}