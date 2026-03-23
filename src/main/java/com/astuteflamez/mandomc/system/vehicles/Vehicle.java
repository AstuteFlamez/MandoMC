package com.astuteflamez.mandomc.system.vehicles;

import java.util.UUID;

import com.astuteflamez.mandomc.system.vehicles.weapons.WeaponSystem;

public class Vehicle {

    private WeaponSystem weaponSystem;
    private VehicleData vehicleData;
    private UUID owner;

    public Vehicle(WeaponSystem weaponSystem, VehicleData vehicleData, UUID owner) {
        this.weaponSystem = weaponSystem;
        this.vehicleData = vehicleData;
        this.owner = owner;
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

    public void setWeaponSystem(WeaponSystem weaponSystem) {
        this.weaponSystem = weaponSystem;
    }

    public void setVehicleData(VehicleData vehicleData) {
        this.vehicleData = vehicleData;
    }

    public void setOwnerUUID(UUID owner) {
        this.owner = owner;
    }
}