package net.mandomc.modules.system;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

import com.ticxo.modelengine.api.ModelEngineAPI;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.listeners.*;
import net.mandomc.content.vehicles.movement.AerialMountController;
import net.mandomc.content.vehicles.movement.SurfaceMountController;
import net.mandomc.core.module.Module;

public class VehicleModule implements Module {

    public static NamespacedKey VEHICLE_KEY;

    private static final HashMap<UUID, Vehicle> activeVehicles = new HashMap<>();

    private final MandoMC plugin;

    public VehicleModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        VEHICLE_KEY = new NamespacedKey(plugin, "vehicle_id");

        ModelEngineAPI.getMountControllerTypeRegistry().register("aerial_controller", AerialMountController.AERIAL);
        ModelEngineAPI.getMountControllerTypeRegistry().register("surface_controller", SurfaceMountController.SURFACE);

        Bukkit.getPluginManager().registerEvents(new SpawnListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new MountListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new PickupListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new VehicleCanisterInteractListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new DamageListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new DeathListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new RepairListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new ShootListener(), plugin);
    }

    @Override
    public void disable() {
        activeVehicles.clear();
    }

    public static HashMap<UUID, Vehicle> getActiveVehicles() {
        return activeVehicles;
    }
}