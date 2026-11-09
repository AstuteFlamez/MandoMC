package net.mandomc.gameplay.vehicles;

/**
 * Legacy thrust/boost semantics without fuel; deterministic and independent of the rendering
 * plugin.
 */
public final class VehicleMotion {
    public record Input(
            double forward, double side, boolean jump, boolean sneak, float yaw, float pitch) {}

    public record Step(double x, double y, double z, float pitch, float roll, boolean dismount) {}

    private boolean thrust, previousForward;
    private double speed, roll;
    private int boostTicks, cooldown;

    public Step tick(VehicleDefinition d, Input input, boolean ground, double verticalVelocity) {
        if (input.sneak && (!d.aerial() || ground)) {
            stop();
            return new Step(0, 0, 0, 0, 0, true);
        }
        if (boostTicks > 0) boostTicks--;
        if (cooldown > 0) cooldown--;
        boolean forward = input.forward > 0;
        if (d.aerial()) {
            if (input.forward < 0) {
                thrust = false;
                boostTicks = 0;
            } else if (forward && !previousForward) {
                if (!thrust) thrust = true;
                else if (cooldown == 0) {
                    boostTicks = d.boostTicks();
                    cooldown = d.boostCooldown();
                }
            }
        }
        previousForward = forward;
        double target =
                d.aerial()
                        ? (thrust ? d.speed() * (boostTicks > 0 ? d.boost() : 1) : 0)
                        : d.speed() * Math.max(-.4, Math.min(1, input.forward));
        speed =
                towards(
                        speed,
                        target,
                        Math.abs(target) > Math.abs(speed) ? d.acceleration() : d.braking());
        float pitch = d.aerial() ? (float) Math.clamp(input.pitch, -d.pitch(), d.pitch()) : 0;
        double yawRad = Math.toRadians(input.yaw), pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad) * speed;
        double z = Math.cos(yawRad) * Math.cos(pitchRad) * speed;
        double y = d.aerial() ? -Math.sin(pitchRad) * speed : verticalVelocity;
        if (d.aerial()) {
            if (input.jump) y += d.speed() * .4;
            if (input.sneak) y -= d.speed() * .4;
        } else {
            x += Math.cos(yawRad) * input.side * d.speed() * .35;
            z += Math.sin(yawRad) * input.side * d.speed() * .35;
            if (input.jump && ground) y = .42;
        }
        roll += ((d.aerial() ? -input.side * d.roll() : 0) - roll) * .15;
        return new Step(x, y, z, pitch, (float) roll, false);
    }

    static double towards(double current, double target, double delta) {
        return current < target
                ? Math.min(current + delta, target)
                : Math.max(current - delta, target);
    }

    public void stop() {
        thrust = false;
        speed = 0;
        boostTicks = 0;
        previousForward = false;
        roll = 0;
    }
}
