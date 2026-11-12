package net.mandomc.gameplay.vehicles;

import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleBoltPoseTest {
    @Test public void visibleLongAxisFollowsVelocityAtEveryHeadingAndPitch() {
        for (Vector velocity : java.util.List.of(new Vector(0,0,4), new Vector(0,0,-4), new Vector(4,0,0),
                new Vector(-4,0,0), new Vector(0,4,0), new Vector(0,-4,0), new Vector(2,1,-3))) {
            var pose = VehicleBoltPose.facing(velocity);
            // Actual model element is elongated in Z, with an intrinsic +45-degree Y rotation.
            var visible = new Vector3f(0,0,1).rotateY((float)Math.toRadians(45));
            pose.getLeftRotation().transform(visible);
            var expected = velocity.clone().normalize();
            assertEquals(expected.getX(), visible.x, 1e-6);
            assertEquals(expected.getY(), visible.y, 1e-6);
            assertEquals(expected.getZ(), visible.z, 1e-6);
        }
    }
    @Test public void zeroVelocityCannotCreateAnInvalidDisplayPose() {
        assertThrows(IllegalArgumentException.class, () -> VehicleBoltPose.facing(new Vector()));
    }
}
