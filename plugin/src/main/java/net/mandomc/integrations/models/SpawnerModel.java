package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.ArmorStand;

/** A miniature existing creature rig, with no Mythic mob, AI, hitbox, mounts or loot. */
public final class SpawnerModel implements AutoCloseable {
    private final ArmorStand base;
    private final ModeledEntity modeled;
    private final ActiveModel active;
    public static boolean available(String id){return ModelEngineAPI.getBlueprint(id)!=null;}
    public static void removeStale(org.bukkit.entity.Entity entity) {
        var modeled=ModelEngineAPI.removeModeledEntity(entity.getUniqueId());
        if(modeled!=null&&!modeled.isDestroyed())modeled.destroy();
        entity.remove();
    }
    public SpawnerModel(ArmorStand base,String id,double scale) {
        this.base=base;
        var blueprint=ModelEngineAPI.getBlueprint(id);
        if(blueprint==null)throw new IllegalStateException("Missing spawner preview model "+id);
        if(!Double.isFinite(scale)||scale<=0||scale>.5)throw new IllegalArgumentException("Invalid miniature scale");
        modeled=ModelEngineAPI.getOrCreateModeledEntity(base);
        modeled.setSaved(false);modeled.setBaseEntityVisible(false);
        active=ModelEngineAPI.createActiveModel(id);active.setScale(scale);
        active.setCanHurt(false);active.setHitboxVisible(false);active.setMainHitbox(false);active.setRenderFire(false);
        modeled.addModel(active,false);
        active.getMountManager().ifPresent(m->{m.setCanDrive(false);m.setCanRide(false);});
        modeled.setModelRotationLocked(true);active.setModelRotationLocked(true);active.setLockYaw(true);active.setLockPitch(true);
        if(blueprint.getAnimations().containsKey("idle"))active.getAnimationHandler().playAnimation("idle",0,0,1,true);
        rotate(0);
    }
    public void rotate(float yaw) {
        if(!valid())return;
        base.setRotation(yaw,0);modeled.setYBodyRotImmediately(yaw);modeled.setYHeadRotImmediately(yaw);modeled.setXHeadRotImmediately(0);
        active.setLockedYBodyRot(yaw);active.setLockedYHeadRot(yaw);active.setLockedXHeadRot(0);
    }
    public boolean valid(){return base.isValid()&&!modeled.isDestroyed()&&!active.isDestroyed()&&!active.isRemoved();}
    public ActiveModel active(){return active;}
    @Override public void close() {
        if(ModelEngineAPI.getModeledEntity(base.getUniqueId())==modeled)ModelEngineAPI.removeModeledEntity(base.getUniqueId());
        if(!modeled.isDestroyed())modeled.destroy();
    }
}
