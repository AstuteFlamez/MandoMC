package net.mandomc.gameplay.boba;

import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Torso attachment reconstructed from the pinned 26.1.2 humanoid/player renderer. */
public final class JetpackPose {
    private static final float PLAYER_MODEL_SCALE = .9375f;
    private static final float MODEL_ORIGIN = 1.501f;
    private static final float CONTACT_GAP = .005f;

    public record Pose(Location center, Quaternionf rotation) {
        /** Geometry orientation after the client's built-in item-display Y half turn. */
        public Quaternionf modelRotation() { return new Quaternionf(rotation).rotateY((float)Math.PI); }
    }

    /** Renderer state at a server tick; native animation counters remain owned by Minecraft. */
    public record Frame(float bodyYaw, float pitch, boolean crouching, boolean gliding, float flyingTicks,
                        float swimAmount, boolean swimmingPose, boolean inWater, float flightBank,
                        float attackTwist, float scale, boolean armored) { }

    private JetpackPose() { }

    /** Retains the existing public API for a settled, armored standing/crouching/gliding pose. */
    public static Pose at(Location feet, float bodyYaw, float pitch, boolean sneaking, boolean gliding) {
        return at(feet,new Frame(bodyYaw,pitch,sneaking,gliding,10,0,false,false,0,0,1,true));
    }

    public static Pose at(Location feet, Frame frame) {
        float glide = Math.clamp(frame.flyingTicks()*frame.flyingTicks()/100f,0,1);
        float lean = frame.gliding() ? glide*(-90-frame.pitch())
                : frame.swimAmount()*(frame.inWater() ? -90-frame.pitch() : -90);
        var root = new Quaternionf().rotationY((float)Math.toRadians(180-frame.bodyYaw()))
                .rotateX((float)Math.toRadians(lean));
        if(frame.gliding())root.rotateY(frame.flightBank());
        // HumanoidModel uses exactly 0.5 radians and a 3.2-pixel shoulder shift for crouching.
        // Its down-positive model Y is reversed by LivingEntityRenderer's model matrix.
        float crouch = frame.crouching() ? .5f : 0;
        var torso = new Quaternionf().rotationYXZ(-frame.attackTwist(),-crouch,0);
        var geometry = new Quaternionf(root).mul(torso);
        float modelScale = PLAYER_MODEL_SCALE*frame.scale();
        var offset = new Vector3f(0,(MODEL_ORIGIN-(frame.crouching() ? 3.2f/16 : 0))*modelScale,0);
        // The extracted mounting plate begins at model Z=10 (1/8 block from its item origin).
        // Place it 0.005 blocks beyond the native chest armor, or the skin's jacket layer.
        float backSurface = (frame.armored() ? 3f : 2.25f)/16*PLAYER_MODEL_SCALE;
        offset.add(new Vector3f(0,-6f/16*modelScale,
                (backSurface+CONTACT_GAP-.125f)*frame.scale()).rotate(torso));
        if(!frame.gliding()&&frame.swimAmount()>0&&frame.swimmingPose())offset.add(0,-1,.3f);
        offset.rotate(root);
        if(frame.crouching())offset.add(0,-.125f*frame.scale(),0);
        var center = feet.clone().add(offset.x,offset.y,offset.z);
        center.setYaw(0);center.setPitch(0);
        // ItemDisplayRenderer.submitInner applies this extra half turn after the display transform.
        return new Pose(center,geometry.rotateY((float)Math.PI));
    }
}
