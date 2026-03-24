package net.mandomc.content.vehicles.movement;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.type.Mount;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;
import com.ticxo.modelengine.api.mount.controller.impl.AbstractMountController;
import com.ticxo.modelengine.api.nms.entity.wrapper.MoveController;

import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.VehicleData;
import net.mandomc.content.vehicles.managers.VehicleFuelManager;
import net.mandomc.content.vehicles.managers.VehicleHUDManager;
import net.mandomc.content.vehicles.managers.VehicleManager;
import net.mandomc.modules.system.VehicleModule;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SurfaceMountController extends AbstractMountController {

    public static final MountControllerType SURFACE =
            new MountControllerType(SurfaceMountController::new);

    public SurfaceMountController(Entity entity, Mount mount) {
        super(entity, mount);
    }

    @Override
    public void updateDriverMovement(MoveController controller, ActiveModel model) {

        Entity driver = getEntity();
        if (!(driver instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        VehicleData vehicleData = vehicle.getVehicleData();
        Entity entity = vehicleData.getEntity();
        double speed = vehicleData.getSpeed();

        MountInput input = getInput();

        /* Sneak → dismount */
        if (input.isSneak()) {
            VehicleManager.dismountVehicle(player, vehicle, model);
            return;
        }

        float front = input.getFront();
        float side = input.getSide();

        float yaw = player.getLocation().getYaw();
        double yawRad = Math.toRadians(yaw);

        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vector right = new Vector(Math.cos(yawRad), 0, Math.sin(yawRad));

        Vector movement = forward.multiply(front).add(right.multiply(side));

        boolean moving = movement.lengthSquared() > 0;

        if (moving) {
            movement = movement.normalize().multiply(speed);
        }

        boolean hasGround =
                entity.getLocation().clone().subtract(0, 0.2, 0)
                        .getBlock().getType() != Material.AIR;

        double y = hasGround ? 0 : -0.3;

        controller.setVelocity(
                movement.getX(),
                y,
                movement.getZ()
        );

        model.getModeledEntity()
                .getBase()
                .getLookController()
                .setBodyYaw(yaw);

        /* Fuel consumption */
        VehicleFuelManager.handleFuel(player, vehicle, model);

        /* Unified vehicle HUD */
        VehicleHUDManager.updateHUD(player, vehicle);

        /* SOUND SYSTEM */
        if (!moving) return;

        int soundTicks = VehicleManager.sound.getOrDefault(uuid, 0);

        soundTicks--;

        if (soundTicks <= 0) {

            player.playSound(
                    player.getLocation(),
                    vehicleData.getMovementSound(),
                    1f,
                    1f
            );

            soundTicks = vehicleData.getMovementSoundLength();
        }

        VehicleManager.sound.put(uuid, soundTicks);
    }

    @Override
    public void updatePassengerMovement(MoveController controller, ActiveModel model) {

    }
}