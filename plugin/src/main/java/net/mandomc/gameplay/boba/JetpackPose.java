package net.mandomc.gameplay.boba;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The model is centered on the torso. Model +Z is the back; head yaw is never used for standing. */
public final class JetpackPose {
    public record Pose(Location center, Quaternionf rotation) {
        /** Actual geometry orientation after the client's built-in item-display Y half turn. */
        public Quaternionf modelRotation() { return new Quaternionf(rotation).rotateY((float)Math.PI); }
    }
    private JetpackPose() { }
    public static Pose at(Location feet,float bodyYaw,float lookPitch,boolean sneaking,boolean gliding) {
        float lean=gliding?(float)Math.toRadians(-90-lookPitch):sneaking?(float)Math.toRadians(-25):0;
        var rotation=new Quaternionf().rotationY((float)Math.toRadians(180-bodyYaw)).rotateX(lean);
        var offset=new Vector3f(0,gliding?.48f:sneaking?1.02f:1.125f,0);
        // Player glide origin is low, so the rotated torso pivots forward from the feet.
        if (gliding) {
            var forward=new Vector3f(0,0,.35f).rotateY((float)Math.toRadians(-bodyYaw));
            offset.add(forward);
        } else if (sneaking) {
            offset.add(new Vector3f(0,0,.12f).rotateY((float)Math.toRadians(-bodyYaw)));
        }
        offset.add(new Vector3f(0,0,.04f).rotate(rotation));
        var center=feet.clone().add(offset.x,offset.y,offset.z);
        // Entity yaw/pitch would otherwise apply head rotation a second time on top of the torso matrix.
        center.setYaw(0);center.setPitch(0);
        // ItemDisplayRenderer.submitInner applies Y=PI after the display transform (26.1.2 client).
        // Cancel it here; particle/nozzle calculations use modelRotation(), the final geometry matrix.
        return new Pose(center,rotation.rotateY((float)Math.PI));
    }
}
