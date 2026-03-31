package net.mandomc.core.modules.server;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.NamespacedKey;

import com.ticxo.modelengine.api.ModelEngineAPI;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.listener.DamageListener;
import net.mandomc.gameplay.vehicle.listener.DeathListener;
import net.mandomc.gameplay.vehicle.listener.MountListener;
import net.mandomc.gameplay.vehicle.listener.PickupListener;
import net.mandomc.gameplay.vehicle.listener.RepairListener;
import net.mandomc.gameplay.vehicle.listener.ShootListener;
import net.mandomc.gameplay.vehicle.listener.SpawnListener;
import net.mandomc.gameplay.vehicle.listener.VehicleCanisterInteractListener;
import net.mandomc.gameplay.vehicle.movement.AerialMountController;
import net.mandomc.gameplay.vehicle.movement.SurfaceMountController;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;

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
    private ListenerRegistrar listenerRegistrar;

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
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);
        VEHICLE_KEY = new NamespacedKey(plugin, "vehicle_id");

        ModelEngineAPI.getMountControllerTypeRegistry().register("aerial_controller", AerialMountController.AERIAL);
        ModelEngineAPI.getMountControllerTypeRegistry().register("surface_controller", SurfaceMountController.SURFACE);

        listenerRegistrar.register(new SpawnListener());
        listenerRegistrar.register(new MountListener());
        listenerRegistrar.register(new PickupListener());
        listenerRegistrar.register(new VehicleCanisterInteractListener());
        listenerRegistrar.register(new DamageListener());
        listenerRegistrar.register(new DeathListener());
        listenerRegistrar.register(new RepairListener());
        listenerRegistrar.register(new ShootListener());
    }

    /**
     * Disables the vehicle system, unregisters listeners, and clears active vehicles.
     */
    @Override
    public void disable() {
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
        activeVehicles.clear();
    }

    public static HashMap<UUID, Vehicle> getActiveVehicles() {
        return activeVehicles;
    }
}
