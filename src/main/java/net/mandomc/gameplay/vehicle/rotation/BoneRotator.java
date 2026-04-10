package net.mandomc.gameplay.vehicle.rotation;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.SimpleManualAnimator;
import org.joml.Quaternionf;

/**
 * Wraps a ModelEngine bone with a {@link SimpleManualAnimator} and sets
 * its orientation each tick from explicit yaw/pitch/roll angles.
 *
 * Roll is possible because we bypass Minecraft's yaw-only entity rotation
 * and set a full quaternion on the model bone via the manual animator.
 *
 * This class is intentionally thin -- all smoothing and roll calculation
 * logic lives in the mount controller.
 */
public class BoneRotator {

    private final ModelBone bone;
    private final SimpleManualAnimator animator;
    private final Quaternionf orientation = new Quaternionf();

    /**
     * @param model    the active model to pull the bone from
     * @param boneName bone id (e.g. "body"); falls back silently if missing
     */
    public BoneRotator(ActiveModel model, String boneName) {
        ModelBone resolved = model.getBone(boneName).orElse(null);
        if (resolved != null) {
            SimpleManualAnimator anim = new SimpleManualAnimator(resolved);
            resolved.setManualAnimator(anim);
            this.bone = resolved;
            this.animator = anim;
        } else {
            this.bone = null;
            this.animator = null;
        }
    }

    public boolean isValid() {
        return bone != null && animator != null;
    }

    /**
     * Sets the bone orientation from explicit euler angles (degrees).
     * Called once per tick by the mount controller.
     */
    public void setOrientation(float yawDeg, float pitchDeg, float rollDeg) {
        if (!isValid()) return;
        orientation.identity()
                .rotateY((float) Math.toRadians(yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
        applyToModel();
    }

    /** Resets orientation to identity (level, north-facing). */
    public void reset() {
        orientation.identity();
        applyToModel();
    }

    private void applyToModel() {
        if (!isValid()) return;
        Quaternionf q = animator.getRotation();
        q.set(orientation);
    }
}
