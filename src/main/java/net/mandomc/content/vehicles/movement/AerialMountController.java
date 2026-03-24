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

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

public class AerialMountController extends AbstractMountController {

    public static final MountControllerType AERIAL =
            new MountControllerType(AerialMountController::new);

    public AerialMountController(Entity entity, Mount mount) {
        super(entity, mount);
    }

    @Override
    public void updateDriverMovement(MoveController controller, ActiveModel model) {

        Entity driver = getEntity();
        if (!(driver instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);
        if (vehicle == null) return;

        VehicleData data = vehicle.getVehicleData();
        double speed = data.getSpeed();

        MountInput input = getInput();

        float front = input.getFront();
        float side = input.getSide();

        Location loc = player.getLocation();

        float yaw = loc.getYaw();
        double yawRad = Math.toRadians(yaw);

        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vector right = new Vector(Math.cos(yawRad), 0, Math.sin(yawRad));

        Vector movement = forward.multiply(front).add(right.multiply(side));

        if (movement.lengthSquared() > 0) {
            movement = movement.normalize().multiply(speed);
        }

        double y = 0;

        if (input.isJump()) {
            y = speed;
        }

        if (input.isSneak()) {

            if (controller.isOnGround()) {

                VehicleManager.dismountVehicle(player, vehicle, model);
                return;

            } else {
                y = -speed;
            }
        }

        controller.setVelocity(
                movement.getX(),
                y,
                movement.getZ()
        );

        model.getModeledEntity()
                .getBase()
                .getLookController()
                .setPitch(loc.getPitch());

        VehicleFuelManager.handleFuel(player, vehicle, model);

        VehicleHUDManager.updateHUD(player, vehicle);

        int soundTicks = VehicleManager.sound.getOrDefault(uuid, 0);

        soundTicks--;

        if (soundTicks <= 0) {

            player.playSound(
                player.getLocation(),
                vehicle.getVehicleData().getMovementSound(),
                1f,
                1f
            );

            soundTicks = vehicle.getVehicleData().getMovementSoundLength();
        }

        VehicleManager.sound.put(uuid, soundTicks);
    }

    @Override
    public void updatePassengerMovement(MoveController controller, ActiveModel model) {

    }
}