package net.mandomc.system.vehicles.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;

import net.mandomc.modules.system.VehicleModule;
import net.mandomc.system.vehicles.Vehicle;
import net.mandomc.system.vehicles.VehicleData;
import net.mandomc.system.vehicles.VehicleRegistry;
import net.mandomc.system.vehicles.config.VehicleConfig;
import net.mandomc.system.vehicles.movement.AerialMountController;
import net.mandomc.system.vehicles.movement.SurfaceMountController;

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

public class VehicleManager {

    public static HashMap<UUID, Integer> sound = new HashMap<>();

    /* =========================
       VEHICLE LIFECYCLE
    ========================= */

    public static void pickupVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleData data = vehicle.getVehicleData();
        ItemStack item = data.getItem();

        destroyVehicle(player);

        item.setAmount(1);
        player.getInventory().addItem(item);

        player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aVehicle picked up.");
    }

    public static void destroyVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);

        if (modeledEntity != null) {
            modeledEntity.destroy();
        }

        entity.remove();
    }

    public static void explodeVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();

        Location loc = entity.getLocation();
        World world = entity.getWorld();

        /* Sounds */
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

        /* Particles */
        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.spawnParticle(Particle.FLAME, loc, 60, 1.5, 1.5, 1.5, 0.05);
        world.spawnParticle(Particle.SMOKE, loc, 80, 2, 2, 2, 0.02);
        world.spawnParticle(Particle.CRIT, loc, 40, 1.5, 1.5, 1.5, 0.2);

        /* Knockback */
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

        if (modeledEntity != null) {
            modeledEntity.destroy();
        }

        entity.remove();
    }

    /* =========================
    MOUNT
    ========================= */

    public static void mountVehicle(Player player, Vehicle vehicle) {

        UUID uuid = player.getUniqueId();
        VehicleData data = vehicle.getVehicleData();
        ActiveModel model = data.getActiveModel();

        // Enable entity
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

        player.playSound(
                player.getLocation(),
                data.getMovementSound(),
                1f,
                1f
        );
    }

    /* =========================
    DISMOUNT
    ========================= */

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

    /* =========================
    CONTROLLER RESOLUTION
    ========================= */

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