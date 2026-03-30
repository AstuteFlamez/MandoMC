package net.mandomc.core.modules.system;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

import com.ticxo.modelengine.api.ModelEngineAPI;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.listeners.DamageListener;
import net.mandomc.content.vehicles.listeners.DeathListener;
import net.mandomc.content.vehicles.listeners.MountListener;
import net.mandomc.content.vehicles.listeners.PickupListener;
import net.mandomc.content.vehicles.listeners.RepairListener;
import net.mandomc.content.vehicles.listeners.ShootListener;
import net.mandomc.content.vehicles.listeners.SpawnListener;
import net.mandomc.content.vehicles.listeners.VehicleCanisterInteractListener;
import net.mandomc.content.vehicles.movement.AerialMountController;
import net.mandomc.content.vehicles.movement.SurfaceMountController;
import net.mandomc.core.module.Module;

/**
 * Manages the lifecycle of the vehicle system.
 *
 * Registers ModelEngine mount controllers, all vehicle event listeners,
 * and tracks active vehicles keyed by player UUID.
 */
public class VehicleModule implements Module {

    /**
     * The NamespacedKey used to identify vehicle data stored in item PDCs.
     */
    public static NamespacedKey VEHICLE_KEY;

    private static final HashMap<UUID, Vehicle> activeVehicles = new HashMap<>();

    private final MandoMC plugin;

    /**
     * Creates the vehicle module.
     *
     * @param plugin the plugin instance
     */
    public VehicleModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the vehicle system.
     *
     * Initializes the vehicle NamespacedKey, registers aerial and surface
     * mount controllers with ModelEngine, and registers all vehicle listeners.
     */
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

    /**
     * Disables the vehicle system and clears the active vehicle registry.
     */
    @Override
    public void disable() {
        activeVehicles.clear();
    }

    /**
     * Returns the map of currently active vehicles keyed by player UUID.
     *
     * @return the active vehicle map
     */
    public static HashMap<UUID, Vehicle> getActiveVehicles() {
        return activeVehicles;
    }
}
