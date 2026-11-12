package net.mandomc.gameplay.vehicles;

import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Aligns the recovered red bolt's actual rotated geometry with its world-space velocity. */
public final class VehicleBoltPose {
    private static final float ANGLE = (float) Math.toRadians(45);
    private static final Vector3f AXIS = new Vector3f(0, 0, 1).rotateY(ANGLE);
    // Model element midpoint rotated about its own JSON pivot, then centered by the item renderer.
    private static final Vector3f CENTER = new Vector3f(7.52204f, 6.22978f, 14.5f)
            .sub(1.95361f, 1.07875f, 10.05141f).rotateY(ANGLE)
            .add(1.95361f, 1.07875f, 10.05141f).div(16).sub(.5f, .5f, .5f);

    private VehicleBoltPose() {}

    public static Transformation facing(Vector velocity) {
        if (!Double.isFinite(velocity.lengthSquared()) || velocity.lengthSquared() < 1e-9)
            throw new IllegalArgumentException("Vehicle bolt requires a finite nonzero velocity");
        var target = new Vector3f((float) velocity.getX(), (float) velocity.getY(), (float) velocity.getZ()).normalize();
        var rotation = new Quaternionf().rotationTo(AXIS, target);
        var translation = rotation.transform(new Vector3f(CENTER)).negate();
        return new Transformation(translation, rotation, new Vector3f(1), new Quaternionf());
    }
}
