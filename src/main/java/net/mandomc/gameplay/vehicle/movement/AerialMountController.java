package net.mandomc.gameplay.vehicle.movement;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.type.Mount;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;
import com.ticxo.modelengine.api.mount.controller.impl.AbstractMountController;
import com.ticxo.modelengine.api.nms.entity.wrapper.MoveController;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.manager.SeatManager;
import net.mandomc.gameplay.vehicle.manager.VehicleFuelManager;
import net.mandomc.gameplay.vehicle.manager.VehicleHUDManager;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.gameplay.vehicle.rotation.BoneRotator;
import net.mandomc.gameplay.vehicle.rotation.RotationLimits;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * Arcade-style aerial mount controller.
 *
 * Movement: the vehicle continuously flies in the direction the player
 * is looking once thrust is engaged. W toggles thrust ON, S toggles it OFF.
 * Jump = extra upward, Sneak in air = descend, Sneak on ground = dismount.
 *
 * Model: follows the player's pitch and yaw each tick.
 *
 * Roll: A/D directly control a limited roll angle on the model.
 * When A/D is released the ship auto-levels back to zero roll.
 */
public class AerialMountController extends AbstractMountController {

    public static final MountControllerType AERIAL =
            new MountControllerType(AerialMountController::new);

    private boolean thrusting = false;
    private boolean forwardPressedLastTick = false;
    private float currentRoll = 0f;
    private float lastFlightPitch = 0f;
    private static final float PITCH_RESET_PER_TICK = 1.2f;
    private double currentSpeed = 0.0;
    private FlightState lastFlightState = FlightState.IDLE;
    private long lastStatusTick = -1L;

    private int boostTicksRemaining = 0;
    private int boostCooldownRemaining = 0;
    private boolean boostReadyAnnounced = true;

    private enum FlightState {
        IDLE,
        ACCELERATING,
        DECELERATING,
        CRUISING
    }

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

        // ---- Dismount check ----
        if (input.isSneak() && controller.isOnGround()) {
            SeatManager.dismountSeat(player, vehicle);
            return;
        }

        // ---- Thrust toggle: W press = on, S = off ----
        float front = input.getFront();
        boolean forwardPressed = front > 0;
        boolean forwardPressEdge = forwardPressed && !forwardPressedLastTick;
        if (front < 0) {
            if (thrusting) {
                thrusting = false;
                sendFlightStatus(player, "vehicles.flight.thrust-off", data.getStatusCooldownTicks());
            }
        } else if (forwardPressEdge) {
            if (!thrusting) {
                thrusting = true;
                sendFlightStatus(player, "vehicles.flight.thrust-on", data.getStatusCooldownTicks());
            } else {
                tryActivateBoost(player, data);
            }
        }
        forwardPressedLastTick = forwardPressed;

        // ---- Pitch clamping + gating while thrust is ON ----
        Location eye = player.getEyeLocation();
        RotationLimits limits = data.getRotationLimits();
        float maxPitch = limits != null ? limits.maxPitch() : 30f;
        float maxRoll  = limits != null ? limits.maxRoll()  : 15f;

        float clampedPitch = clamp(eye.getPitch(), -maxPitch, maxPitch);
        if (thrusting) {
            lastFlightPitch = clampedPitch;
        } else {
            lastFlightPitch = moveTowards(lastFlightPitch, 0f, PITCH_RESET_PER_TICK);
        }
        float flightPitch = lastFlightPitch;

        tickBoost(player, data);

        // ---- Movement: fly where you look while thrusting ----
        // Build direction from yaw + clamped pitch so the vehicle
        // cannot dive/climb beyond the configured limit.
        double maxSpeed = data.getSpeed();
        double boostMultiplier = boostTicksRemaining > 0 ? data.getBoostMultiplier() : 1.0;
        double targetSpeed = thrusting ? (maxSpeed * boostMultiplier) : 0.0;
        currentSpeed = moveTowards(
                currentSpeed,
                targetSpeed,
                thrusting ? data.getAccelerationPerTick() : data.getDecelerationPerTick()
        );

        FlightState currentState = classifyFlightState(targetSpeed);
        maybeSendFlightState(player, data, currentState);
        lastFlightState = currentState;

        double vx = 0, vy = 0, vz = 0;
        if (currentSpeed > 0.0001) {
            double yawRad   = Math.toRadians(eye.getYaw());
            double pitchRad = Math.toRadians(flightPitch);
            double cosP     = Math.cos(pitchRad);
            vx = -Math.sin(yawRad) * cosP * currentSpeed;
            vy = -Math.sin(pitchRad) * currentSpeed;
            vz =  Math.cos(yawRad) * cosP * currentSpeed;
        }

        if (input.isJump()) {
            vy += speed;
        }
        if (input.isSneak()) {
            vy -= speed;
        }

        controller.setVelocity(vx, vy, vz);

        // ---- Visual: model follows clamped pitch/yaw, A/D controls roll ----
        BoneRotator rotator = data.getBoneRotator();

        if (rotator != null && rotator.isValid()) {
            float rollSmoothing = data.getRollSmoothing();
            float shakePitch = boostTicksRemaining > 0 ? randomShake(data.getBoostShakeDegrees()) : 0f;

            float side = input.getSide();
            float targetRoll;
            if (side > 0) {
                targetRoll = -maxRoll;      // D = roll right (negative Z rotation)
            } else if (side < 0) {
                targetRoll = maxRoll;       // A = roll left  (positive Z rotation)
            } else {
                targetRoll = 0f;            // auto-level
            }

            currentRoll += (targetRoll - currentRoll) * rollSmoothing;
            if (Math.abs(currentRoll) < 0.1f) currentRoll = 0f;

            rotator.setOrientation(0f, flightPitch + shakePitch, currentRoll);
        }

        // Sync entity orientation: body yaw + pitch (with subtle boost shake)
        var lookController = model.getModeledEntity()
                .getBase()
                .getLookController();
        float shakeYaw = boostTicksRemaining > 0 ? randomShake(data.getBoostShakeDegrees()) : 0f;
        float shakePitch = boostTicksRemaining > 0 ? randomShake(data.getBoostShakeDegrees()) : 0f;
        lookController.setBodyYaw(eye.getYaw() + shakeYaw);
        lookController.setPitch(flightPitch + shakePitch);

        // ---- Fuel, HUD, sound ----
        VehicleFuelManager.handleFuel(player, vehicle, model);
        VehicleHUDManager.updateHUD(player, vehicle);

        int soundTicks = VehicleManager.sound.getOrDefault(uuid, 0);
        soundTicks--;
        if (soundTicks <= 0) {
            player.playSound(
                    player.getLocation(),
                    data.getMovementSound(),
                    1f, 1f
            );
            soundTicks = data.getMovementSoundLength();
        }
        VehicleManager.sound.put(uuid, soundTicks);
    }

    @Override
    public void updatePassengerMovement(MoveController controller, ActiveModel model) {
        Entity rider = getEntity();
        if (!(rider instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getVehicleForPlayer(uuid);
        if (vehicle == null) return;

        MountInput input = getInput();
        if (input.isSneak()) {
            SeatManager.dismountSeat(player, vehicle);
        }
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double moveTowards(double current, double target, double maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (current < target) return Math.min(current + maxDelta, target);
        if (current > target) return Math.max(current - maxDelta, target);
        return current;
    }

    private FlightState classifyFlightState(double targetSpeed) {
        if (currentSpeed <= 0.0001 && targetSpeed <= 0.0001) return FlightState.IDLE;
        if (currentSpeed + 0.0001 < targetSpeed) return FlightState.ACCELERATING;
        if (currentSpeed - 0.0001 > targetSpeed) return FlightState.DECELERATING;
        return FlightState.CRUISING;
    }

    private void maybeSendFlightState(Player player, VehicleData data, FlightState state) {
    }

    private void tryActivateBoost(Player player, VehicleData data) {
        if (boostCooldownRemaining > 0 || boostTicksRemaining > 0) return;
        boostTicksRemaining = data.getBoostDurationTicks();
        boostCooldownRemaining = data.getBoostCooldownTicks();
        boostReadyAnnounced = false;
    }

    private void tickBoost(Player player, VehicleData data) {
        if (boostTicksRemaining > 0) {
            boostTicksRemaining--;
        }

        if (boostCooldownRemaining > 0) {
            boostCooldownRemaining--;
            if (boostCooldownRemaining == 0 && !boostReadyAnnounced) {
                boostReadyAnnounced = true;
            }
        }
    }

    private void sendFlightStatus(Player player, String key, int cooldownTicks) {
        long now = Bukkit.getCurrentTick();
        if (lastStatusTick >= 0 && now - lastStatusTick < Math.max(0, cooldownTicks)) return;
        lastStatusTick = now;
        VehicleHUDManager.pushTransientActionBar(
                player,
                LangManager.get(key),
                Math.max(20, cooldownTicks)
        );
    }

    private static float randomShake(float maxDegrees) {
        if (maxDegrees <= 0f) return 0f;
        return ThreadLocalRandom.current().nextFloat(-maxDegrees, maxDegrees);
    }
}