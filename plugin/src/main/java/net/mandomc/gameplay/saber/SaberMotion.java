package net.mandomc.gameplay.saber;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Distance-based timing and a damped spin plane shared by rendering and blade contact checks. */
public final class SaberMotion {
    private final SaberArc arc;
    private final SaberArc.Style style;
    private final SaberSettings settings;
    private final double yaw;
    private final Vector hand;
    private final Quaternionf tilt = new Quaternionf();
    private Vector previousDirection;
    private double traveled;
    private double speed;
    private double spin;

    public SaberMotion(SaberArc arc, SaberArc.Style style, SaberSettings settings, Vector origin, double yaw) {
        this.arc = arc;
        this.style = style;
        this.settings = settings;
        this.yaw = yaw;
        this.hand = origin.clone();
        this.speed = settings.speed() * .65 / style.tempo();
    }

    public double progress() { return arc.progressAt(traveled); }

    public SaberBlade.Pose initialPose(Vector origin) { return SaberBlade.pose(origin, yaw, 0, 0); }

    public SaberBlade.Pose next(Vector current, Vector ownerHand, Vector targetOffset, boolean recalling) {
        Vector destination;
        if (recalling || progress() >= 1) {
            Vector toHand = ownerHand.clone().subtract(current);
            double distance = toHand.length();
            if (distance > settings.returnSpeed()) toHand.multiply(settings.returnSpeed() / distance);
            destination = current.clone().add(toHand);
        } else {
            double turn = arc.distanceAt(arc.turnTime());
            double aroundTurn = Math.max(1, Math.min(3, arc.length() * .09));
            double blend = smooth((traveled - turn + aroundTurn) / (2 * aroundTurn));
            double cruise = settings.speed() * (1 - blend) + settings.returnSpeed() * blend;
            double proximity = Math.max(0, 1 - Math.abs(traveled - turn) / aroundTurn);
            double catchEase = .65 + .35 * smooth((arc.length() - traveled) / 3);
            double desiredSpeed = cruise * (1 - .35 * smooth(proximity)) * catchEase / style.tempo();
            desiredSpeed = Math.min(desiredSpeed, arc.speedLimit(traveled + speed * .5));
            speed += Math.clamp(desiredSpeed - speed, -.12, .12);
            traveled = Math.min(arc.length(), traveled + speed);
            // Follow walking/sprinting without translating the whole return abruptly when the owner turns.
            Vector adjustment = ownerHand.clone().subtract(hand).multiply(.5);
            if (adjustment.lengthSquared() > .64) adjustment.normalize().multiply(.8);
            hand.add(adjustment);
            destination = arc.position(progress(), targetOffset, hand);
        }
        Vector velocity = destination.clone().subtract(current);
        updateTilt(velocity);
        spin = (spin + style.spin()) % 360;
        Quaternionf rotation = new Quaternionf(tilt)
                .rotateY((float) Math.toRadians(-yaw)).rotateX((float) (Math.PI / 2))
                .rotateZ((float) Math.toRadians(spin));
        return new SaberBlade.Pose(destination, rotation, 1);
    }

    private void updateTilt(Vector velocity) {
        if (velocity.lengthSquared() < 1e-8) return;
        Vector direction = velocity.clone().normalize();
        Vector horizontal = direction.clone().setY(0);
        double horizontalLength = horizontal.length();
        if (horizontalLength < 1e-4) {
            double radians = Math.toRadians(yaw);
            horizontal = new Vector(-Math.sin(radians), 0, Math.cos(radians));
        } else horizontal.multiply(1 / horizontalLength);
        double pitch = Math.clamp(Math.atan2(direction.getY(), horizontalLength) * .7,
                Math.toRadians(-50), Math.toRadians(50));
        double turn = 0;
        if (previousDirection != null && horizontalLength > .1) {
            Vector prior = previousDirection.clone().setY(0);
            if (prior.lengthSquared() > .01) {
                prior.normalize();
                turn = Math.atan2(prior.clone().crossProduct(horizontal).getY(), prior.dot(horizontal));
            }
        }
        double bank = Math.clamp(turn * 2.2, -Math.toRadians(style.bank()), Math.toRadians(style.bank()));
        Vector side = new Vector(horizontal.getZ(), 0, -horizontal.getX());
        Vector normal = new Vector(0, Math.cos(pitch), 0)
                .subtract(horizontal.clone().multiply(Math.sin(pitch)))
                .add(side.multiply(Math.tan(bank))).normalize();
        Quaternionf desired = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                new Vector3f((float) normal.getX(), (float) normal.getY(), (float) normal.getZ()));
        double angle = 2 * Math.acos(Math.min(1, Math.abs(tilt.dot(desired))));
        float amount = (float) Math.min(.3, Math.toRadians(6) / Math.max(angle, 1e-6));
        tilt.slerp(desired, amount).normalize();
        previousDirection = direction;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
