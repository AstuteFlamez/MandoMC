package net.mandomc.gameplay.vehicle.manager;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.movement.AerialMountController;
import net.mandomc.gameplay.vehicle.movement.SurfaceMountController;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.core.LangManager;

/**
 * Core operations for vehicle lifecycle management.
 *
 * Handles pickup, destruction, explosion, mounting, dismounting,
 * and mount controller resolution.
 */
public class VehicleManager {

    /**
     * Tracks sound task ids per player UUID for looping movement sounds.
     */
    public static HashMap<UUID, Integer> sound = new HashMap<>();

    /**
     * Picks up the vehicle owned by the given player.
     *
     * Destroys the vehicle model and entity, then returns the item to
     * the player's inventory.
     *
     * @param player the vehicle owner
     */
    public static void pickupVehicle(Player player) {
        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        VehicleData data = vehicle.getVehicleData();
        ItemStack item = data.getItem();

        destroyVehicle(player);

        item.setAmount(1);
        player.getInventory().addItem(item);

        player.sendMessage(LangManager.get("vehicles.picked-up"));
    }

    /**
     * Destroys the vehicle owned by the given player.
     *
     * Removes the ModelEngine model and the backing entity.
     *
     * @param player the vehicle owner
     */
    public static void destroyVehicle(Player player) {
        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
        if (modeledEntity != null) modeledEntity.destroy();

        entity.remove();
    }

    /**
     * Explodes the vehicle owned by the given player.
     *
     * Plays explosion sounds and particles, applies knockback to nearby players,
     * then destroys the vehicle.
     *
     * @param player the vehicle owner
     */
    public static void explodeVehicle(Player player) {
        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();
        Location loc = entity.getLocation();
        World world = entity.getWorld();

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.spawnParticle(Particle.FLAME, loc, 60, 1.5, 1.5, 1.5, 0.05);
        world.spawnParticle(Particle.SMOKE, loc, 80, 2, 2, 2, 0.02);
        world.spawnParticle(Particle.CRIT, loc, 40, 1.5, 1.5, 1.5, 0.2);

        for (Entity nearby : world.getNearbyEntities(loc, 4, 3, 4)) {
            if (nearby instanceof Player nearbyPlayer) {
                Vector knockback = nearbyPlayer.getLocation()
                        .toVector()
                        .subtract(loc.toVector())
                        .normalize()
                        .multiply(0.6)
                        .setY(0.4);
                nearbyPlayer.setVelocity(knockback);
            }
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
        if (modeledEntity != null) modeledEntity.destroy();

        entity.remove();
    }

    /**
     * Mounts the player into the given vehicle.
     *
     * Enables the entity AI, assigns the appropriate mount controller,
     * plays the mount animation, and starts the movement sound.
     *
     * @param player the player to mount
     * @param vehicle the vehicle to mount into
     */
    public static void mountVehicle(Player player, Vehicle vehicle) {
        UUID uuid = player.getUniqueId();
        VehicleData data = vehicle.getVehicleData();
        ActiveModel model = data.getActiveModel();

        data.getEntity().setAI(true);
        data.getEntity().setGravity(false);

        MountControllerType controller = resolveController(vehicle);

        model.getMountManager().ifPresent(mountManager -> {
            mountManager.setCanDrive(true);
            mountManager.mountDriver(player, controller);
        });

        AnimationHandler handler = model.getAnimationHandler();
        handler.playAnimation("mount", 0.3, 0.3, 1, true);

        sound.put(uuid, data.getMovementSoundLength());

        player.playSound(player.getLocation(), data.getMovementSound(), 1f, 1f);
    }

    /**
     * Dismounts the player from the given vehicle.
     *
     * Stops the movement sound, disables entity AI, and plays the dismount animation.
     *
     * @param player the player to dismount
     * @param vehicle the vehicle being dismounted
     * @param model the active model of the vehicle
     */
    public static void dismountVehicle(Player player, Vehicle vehicle, ActiveModel model) {
        UUID uuid = player.getUniqueId();
        VehicleData data = vehicle.getVehicleData();

        model.getMountManager().ifPresent(m -> m.dismountDriver());

        sound.remove(uuid);
        player.stopSound(data.getMovementSound());

        data.getEntity().setAI(false);
        data.getEntity().setGravity(true);

        AnimationHandler handler = model.getAnimationHandler();
        handler.stopAnimation("mount");
        handler.playAnimation("dismount", 0.2, 0.2, 1, false);
    }

    /**
     * Resolves the mount controller type for the given vehicle.
     *
     * Returns the aerial controller if the vehicle config specifies AERIAL movement,
     * otherwise returns the surface controller as the default.
     *
     * @param vehicle the vehicle to resolve the controller for
     * @return the appropriate mount controller type
     */
    private static MountControllerType resolveController(Vehicle vehicle) {
        String itemId = vehicle.getItemId();
        if (itemId == null) return SurfaceMountController.SURFACE;

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return SurfaceMountController.SURFACE;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return SurfaceMountController.SURFACE;

        String movement = config.getString("vehicle.systems.movement", "SURFACE");

        return movement.equalsIgnoreCase("AERIAL")
                ? AerialMountController.AERIAL
                : SurfaceMountController.SURFACE;
    }
}
