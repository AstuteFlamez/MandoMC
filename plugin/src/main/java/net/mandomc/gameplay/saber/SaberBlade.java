package net.mandomc.gameplay.saber;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared render/collision pose for JustTimm's fixed-context blades, in block units. */
public final class SaberBlade {
    // Derived from the referenced upstream model and its fixed transform, without copying artwork.
    private static final Vector3f MODEL_BLADE_CENTER = new Vector3f(.00625f, 1.34375f, -.09375f);

    public record Pose(Vector center, Quaternionf rotation, double halfLength) {
        public Vector base() { return endpoint(-halfLength); }
        public Vector tip() { return endpoint(halfLength); }
        private Vector endpoint(double distance) {
            Vector3f point = rotation.transform(new Vector3f(0, (float) distance, 0));
            return center.clone().add(new Vector(point.x, point.y, point.z));
        }
        public Transformation transformation() {
            return transformation(MODEL_BLADE_CENTER);
        }
        public Transformation transformation(Vector3f modelCenter) {
            Vector3f translation = rotation.transform(new Vector3f(modelCenter)).negate();
            return new Transformation(translation, new Quaternionf(rotation), new Vector3f(1), new Quaternionf());
        }
        public Pose interpolate(Pose end, double t) {
            return new Pose(center.clone().multiply(1 - t).add(end.center.clone().multiply(t)),
                    new Quaternionf(rotation).slerp(end.rotation, (float) t), halfLength);
        }
        public Vector closest(Vector point) {
            Vector base = base(), axis = tip().subtract(base);
            double t = Math.clamp(point.clone().subtract(base).dot(axis) / axis.lengthSquared(), 0, 1);
            return base.add(axis.multiply(t));
        }
    }

    public static Pose pose(Vector center, double yaw, double bank, double spin) {
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(90 + bank)).rotateZ((float) Math.toRadians(spin));
        return new Pose(center.clone(), rotation, 1.0);
    }

    public static boolean touches(BoundingBox bounds, Pose pose, double radius) {
        BoundingBox box = bounds.clone().expand(radius);
        Vector base = pose.base(), tip = pose.tip(), axis = tip.clone().subtract(base);
        return box.contains(base) || box.contains(tip) || box.rayTrace(base, axis, axis.length()) != null;
    }

    public static int steps(Pose start, Pose end) {
        double dot = Math.min(1, Math.abs(start.rotation.dot(end.rotation)));
        double tipTravel = start.center.distance(end.center) + 2 * Math.acos(dot) * start.halfLength;
        return Math.max(1, (int) Math.ceil(tipTravel / .12));
    }
}
