package net.mandomc.core.modules.server;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.NamespacedKey;

import com.ticxo.modelengine.api.ModelEngineAPI;

import net.mandomc.MandoMC;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.integration.OptionalPluginSupport;
import net.mandomc.core.guis.GUIManager;
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
 * and tracks active vehicles keyed by owner player UUID, as well as an
 * occupant index that maps any rider UUID to their vehicle owner UUID.
 */
public class VehicleModule implements Module {

    /**
     * The NamespacedKey used to identify vehicle data stored in item PDCs.
     */
    public static NamespacedKey VEHICLE_KEY;

    /** Maps owner UUID → their spawned vehicle. */
    private static final HashMap<UUID, Vehicle> activeVehicles = new HashMap<>();
    /** Maps vehicle entity UUID → owner UUID for O(1) entity event lookups. */
    private static final HashMap<UUID, UUID> entityIndex = new HashMap<>();

    /**
     * Maps any non-owner rider UUID → the owner UUID of the vehicle they're in.
     * Used to resolve which vehicle a passenger or gunner belongs to.
     */
    private static final HashMap<UUID, UUID> occupantIndex = new HashMap<>();

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

        if (!OptionalPluginSupport.hasModelEngine()) {
            plugin.getLogger().warning("Vehicle module disabled: ModelEngine is unavailable.");
            return;
        }
        if (!OptionalPluginSupport.hasWeaponMechanics()) {
            plugin.getLogger().warning("Vehicle module disabled: WeaponMechanics is unavailable.");
            return;
        }

        GUIManager guiManager = registry.get(GUIManager.class);
        MainConfig mainConfig = registry.get(MainConfig.class);
        if (mainConfig == null) {
            plugin.getLogger().warning("Vehicle module disabled: MainConfig is unavailable.");
            return;
        }

        ModelEngineAPI.getMountControllerTypeRegistry().register("aerial_controller", AerialMountController.AERIAL);
        ModelEngineAPI.getMountControllerTypeRegistry().register("surface_controller", SurfaceMountController.SURFACE);

        listenerRegistrar.register(new SpawnListener(mainConfig));
        listenerRegistrar.register(new MountListener(guiManager));
        listenerRegistrar.register(new PickupListener());
        listenerRegistrar.register(new VehicleCanisterInteractListener());
        listenerRegistrar.register(new DamageListener());
        listenerRegistrar.register(new DeathListener());
        listenerRegistrar.register(new RepairListener());
        listenerRegistrar.register(new ShootListener());
    }

    /**
     * Disables the vehicle system, unregisters listeners, and clears all tracking maps.
     */
    @Override
    public void disable() {
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
        activeVehicles.clear();
        occupantIndex.clear();
        entityIndex.clear();
    }

    // -------------------------------------------------------------------------
    // Vehicle lookups
    // -------------------------------------------------------------------------

    /**
     * Returns the active vehicle map keyed by owner UUID.
     */
    public static HashMap<UUID, Vehicle> getActiveVehicles() {
        return activeVehicles;
    }

    /**
     * Returns the vehicle for the given player, whether they are the owner,
     * a passenger, or a gunner.
     *
     * Checks {@code activeVehicles} first (covers drivers/owners), then falls
     * back to the {@code occupantIndex} for non-owner riders.
     *
     * @param playerUUID the UUID of any player currently in a vehicle
     * @return the vehicle, or null if the player is not in any vehicle
     */
    public static Vehicle getVehicleForPlayer(UUID playerUUID) {
        Vehicle direct = activeVehicles.get(playerUUID);
        if (direct != null) return direct;

        UUID ownerUUID = occupantIndex.get(playerUUID);
        if (ownerUUID == null) return null;

        return activeVehicles.get(ownerUUID);
    }

    /**
     * Returns the vehicle backing the given spawned entity UUID.
     *
     * @param entityUuid entity UUID from a Bukkit event
     * @return matching vehicle, or null if the entity is not tracked as a vehicle
     */
    public static Vehicle getVehicleByEntity(UUID entityUuid) {
        UUID ownerUuid = entityIndex.get(entityUuid);
        if (ownerUuid == null) {
            return null;
        }
        return activeVehicles.get(ownerUuid);
    }

    /**
     * Registers a spawned vehicle and updates owner/entity indexes.
     *
     * @param ownerUuid owning player UUID
     * @param vehicle vehicle instance
     */
    public static void registerVehicle(UUID ownerUuid, Vehicle vehicle) {
        activeVehicles.put(ownerUuid, vehicle);
        if (vehicle.getVehicleData() == null || vehicle.getVehicleData().getEntity() == null) {
            return;
        }
        entityIndex.put(vehicle.getVehicleData().getEntity().getUniqueId(), ownerUuid);
    }

    /**
     * Unregisters an owner's active vehicle and clears related indexes.
     *
     * @param ownerUuid owning player UUID
     * @return removed vehicle, or null if none existed
     */
    public static Vehicle unregisterVehicle(UUID ownerUuid) {
        Vehicle removed = activeVehicles.remove(ownerUuid);
        if (removed == null || removed.getVehicleData() == null || removed.getVehicleData().getEntity() == null) {
            return removed;
        }
        entityIndex.remove(removed.getVehicleData().getEntity().getUniqueId());
        return removed;
    }

    /**
     * Unregisters a vehicle by entity UUID and clears related indexes.
     *
     * @param entityUuid backing entity UUID
     * @return removed vehicle, or null if none was mapped
     */
    public static Vehicle unregisterVehicleByEntity(UUID entityUuid) {
        UUID ownerUuid = entityIndex.remove(entityUuid);
        if (ownerUuid == null) {
            return null;
        }
        return activeVehicles.remove(ownerUuid);
    }

    // -------------------------------------------------------------------------
    // Occupant tracking
    // -------------------------------------------------------------------------

    /**
     * Registers a non-owner rider as an occupant of the given owner's vehicle.
     *
     * @param playerUUID UUID of the riding player
     * @param ownerUUID  UUID of the vehicle owner
     */
    public static void registerOccupant(UUID playerUUID, UUID ownerUUID) {
        occupantIndex.put(playerUUID, ownerUUID);
    }

    /**
     * Removes a rider from the occupant index.
     *
     * @param playerUUID UUID of the player to unregister
     */
    public static void unregisterOccupant(UUID playerUUID) {
        occupantIndex.remove(playerUUID);
    }
}