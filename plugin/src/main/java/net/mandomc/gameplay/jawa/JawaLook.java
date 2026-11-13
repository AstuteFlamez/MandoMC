package net.mandomc.gameplay.jawa;

/** Same comfortable head cone and shortest-arc follow as the stationary spawn characters. */
public final class JawaLook {
    public record Pose(float body, float head, float pitch) { }
    private JawaLook() { }

    public static float wrap(float angle) {
        return (float) Math.IEEEremainder(angle, 360);
    }

    private static float approach(float current, float target, float limit) {
        return wrap(current + Math.max(-limit, Math.min(limit, wrap(target - current))));
    }

    public static Pose step(float body, float head, float pitch, float yaw, float targetPitch, boolean tracking) {
        float delta = wrap(yaw - body);
        float bodyTarget = tracking ? body + Math.copySign(Math.max(0, Math.abs(delta) - 45), delta) : yaw;
        float nextBody = approach(body, bodyTarget, 12);
        float headTarget = tracking ? nextBody + Math.max(-60, Math.min(60, wrap(yaw - nextBody))) : yaw;
        float nextHead = approach(head, headTarget, 30);
        nextHead = wrap(nextBody + Math.max(-60, Math.min(60, wrap(nextHead - nextBody))));
        float desiredPitch = Math.max(-35, Math.min(35, targetPitch));
        return new Pose(nextBody, nextHead, pitch + Math.max(-15, Math.min(15, desiredPitch - pitch)));
    }
}
