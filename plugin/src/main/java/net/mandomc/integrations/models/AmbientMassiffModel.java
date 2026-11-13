package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.Mob;

/** Existing Massiff rig only: no Mythic definition, target selectors, attacks, sounds or loot. */
public final class AmbientMassiffModel implements AutoCloseable {
    private final ModeledEntity modeled;
    private final ActiveModel active;
    private Boolean walking;
    public AmbientMassiffModel(Mob base) {
        if (ModelEngineAPI.getBlueprint("massiff") == null) throw new IllegalStateException("Missing existing massiff blueprint");
        var existing = ModelEngineAPI.getModeledEntity(base);
        if (existing != null && existing.isDestroyed()) ModelEngineAPI.removeModeledEntity(base);
        modeled = ModelEngineAPI.getOrCreateModeledEntity(base);
        // A renderer reload can leave a destroyed model registered on a still-valid body.
        modeled.getModel("massiff").ifPresent(old -> { modeled.removeModel("massiff"); if (!old.isDestroyed()) old.destroy(); });
        modeled.setSaved(false); modeled.setBaseEntityVisible(false);
        active = ModelEngineAPI.createActiveModel("massiff");
        active.setCanHurt(false); active.setHitboxVisible(false); active.setMainHitbox(false); active.setRenderFire(false);
        // The native default state machine otherwise starts/stops these same clips between
        // movement samples. Its aliases must not own the ambient controller's idle/walk keys.
        var handler = active.getAnimationHandler();
        for (var state : com.ticxo.modelengine.api.animation.ModelState.values())
            handler.setDefaultProperty(new com.ticxo.modelengine.api.animation.handler.AnimationHandler.DefaultProperty(
                    state, "mandomc_ambient_manual", 0, 0, 1));
        modeled.addModel(active, false);
        active.getMountManager().ifPresent(m -> { m.setCanDrive(false); m.setCanRide(false); });
        active.getAnimationHandler().forceStopAllAnimations(); walk(false);
    }
    public void walk(boolean move) {
        var handler = active.getAnimationHandler();
        String clip = move ? "walk" : "idle";
        String other = move ? "idle" : "walk";
        if (handler.isPlayingAnimation(other)) handler.forceStopAnimation(other);
        var animation = handler.getAnimation(clip);
        // Cached intent is insufficient after a vendor reload or lost animation property.
        // A LOOP property may report isFinished() at its length boundary before wrapping;
        // only an ended/stopping property is lost. Replacing a boundary frame restarts lerp-in.
        if (walking == null || walking != move || animation == null || animation.isEnded() || animation.getPhase() == com.ticxo.modelengine.api.animation.property.IAnimationProperty.Phase.LERPOUT) {
            animation = handler.playAnimation(clip, .1, .1, 1, true);
            if (animation == null) throw new IllegalStateException("Missing Massiff ambient animation");
            // API blend durations are seconds, not ticks. Merge the short transition
            // into the advancing gait rather than holding its first pose during lerp-in.
            animation.setMergeLerp(true);
            animation.setForceLoopMode(com.ticxo.modelengine.api.animation.BlueprintAnimation.LoopMode.LOOP);
        }
        walking = move;
    }
    public static void removeStale(org.bukkit.entity.Entity entity) {
        var model = ModelEngineAPI.getModeledEntity(entity.getUniqueId()); if (model != null) model.destroy(); entity.remove();
    }
    public boolean valid() {
        return !modeled.isDestroyed() && !active.isDestroyed() && !active.isRemoved()
                && ModelEngineAPI.getModeledEntity(modeled.getBase().getUUID()) == modeled
                && modeled.getModel("massiff").orElse(null) == active;
    }
    public ActiveModel active() { return active; }
    @Override public void close() {
        if (ModelEngineAPI.getModeledEntity(modeled.getBase().getUUID()) == modeled)
            ModelEngineAPI.removeModeledEntity(modeled.getBase().getUUID());
        if (!modeled.isDestroyed()) modeled.destroy();
    }
}
