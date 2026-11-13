package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorData;
import net.mandomc.gameplay.decor.DecorDefinition;
import org.bukkit.entity.ArmorStand;

/** Renders existing rigs only; never registers player-vehicle ownership, controllers or combat AI. */
public final class DecorModel implements AutoCloseable {
    private final ModeledEntity modeled;
    private final ActiveModel active;
    public DecorModel(ArmorStand base,DecorDefinition definition){
        if(ModelEngineAPI.getBlueprint(definition.model())==null)throw new IllegalStateException("Missing decor blueprint "+definition.model());
        var existing=ModelEngineAPI.getModeledEntity(base);
        if(existing!=null&&existing.isDestroyed())ModelEngineAPI.removeModeledEntity(base);
        modeled=ModelEngineAPI.getOrCreateModeledEntity(base);
        modeled.getModel(definition.model()).ifPresent(old->{modeled.removeModel(definition.model());if(!old.isDestroyed())old.destroy();});modeled.setSaved(false);modeled.setBaseEntityVisible(false);
        active=ModelEngineAPI.createActiveModel(definition.model());active.setScale(definition.scale());
        // This flag sends an invisible INTERACTION target to clients; it does not show the native base.
        active.setCanHurt(false);active.setHitboxVisible(definition.npc());active.setMainHitbox(false);active.setRenderFire(false);
        modeled.addModel(active,false);
        if (definition.npc()) {
            var head = active.getBone("head").orElseThrow(() ->
                    new IllegalStateException("Missing decor head bone " + definition.model()));
            // Untagged bones already carry a hidden HEAD fallback, which does not drive a head.
            // Replace that behavior rather than treating hasBoneBehavior(HEAD) as proof of tracking.
            head.removeBoneBehavior(BoneBehaviorTypes.HEAD);
            addHeadBehavior(head, BoneBehaviorTypes.HEAD);
            // Local head rotation enters the hierarchy before child transforms, so Boba's visor follows too.
            head.getBoneBehavior(BoneBehaviorTypes.HEAD).orElseThrow().setLocal(true);
            refreshHeadChildren(head);
        }
        modeled.setModelRotationLocked(true);modeled.setYBodyRotImmediately(definition.yaw());modeled.setYHeadRotImmediately(definition.yaw());modeled.setXHeadRotImmediately(definition.pitch());
        active.setModelRotationLocked(true);active.setLockYaw(true);active.setLockPitch(true);
        active.setLockedYBodyRot(definition.yaw());active.setLockedYHeadRot(definition.yaw());active.setLockedXHeadRot(definition.pitch());
        active.getMountManager().ifPresent(manager->{manager.setCanDrive(false);manager.setCanRide(false);});
        active.getAnimationHandler().forceStopAllAnimations();
        for(String animation:definition.animations()){
            var property=active.getAnimationHandler().playAnimation(animation,0,0,definition.npc()?1:20,true);
            if(property==null)throw new IllegalStateException("Missing decor animation "+definition.model()+":"+animation);
            if(!definition.npc())property.setForceLoopMode(BlueprintAnimation.LoopMode.HOLD);
        }
    }
    public void face(ArmorStand base,float targetYaw,float targetPitch,boolean tracking){
        var pose=net.mandomc.gameplay.decor.DecorLook.step(modeled.getYBodyRot(),modeled.getYHeadRot(),modeled.getXHeadRot(),targetYaw,targetPitch,tracking);
        base.setRotation(pose.body(),pose.pitch());
        var controller=modeled.getBase().getBodyRotationController();
        controller.setYBodyRot(pose.body());controller.setYHeadRot(pose.head());controller.setXHeadRot(pose.pitch());
        modeled.setYBodyRotImmediately(pose.body());modeled.setYHeadRotImmediately(pose.head());modeled.setXHeadRotImmediately(pose.pitch());
        active.setLockedYBodyRot(pose.body());active.setLockedYHeadRot(pose.head());active.setLockedXHeadRot(pose.pitch());
    }
    public ActiveModel active(){return active;}
    private static <T extends com.ticxo.modelengine.api.model.bone.behavior.BoneBehavior> void addHeadBehavior(
            com.ticxo.modelengine.api.model.bone.ModelBone bone,
            com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorType<T> type) {
        bone.addBoneBehavior(type.getBehaviorProvider().create(
                bone, type, new BoneBehaviorData(java.util.Map.of("local", true, "inherited", true))));
    }
    private static void refreshHeadChildren(com.ticxo.modelengine.api.model.bone.ModelBone parent) {
        for (var child : parent.getChildren().values()) {
            child.forBehaviors(behavior -> behavior.onParentSwap(parent));
            refreshHeadChildren(child);
        }
    }
    public boolean valid(){return !modeled.isDestroyed()&&!active.isDestroyed()&&!active.isRemoved()
            &&ModelEngineAPI.getModeledEntity(modeled.getBase().getUUID())==modeled
            &&modeled.getModel(active.getBlueprint().getName()).orElse(null)==active;}
    public ModeledEntity modeled(){return modeled;}
    @Override public void close(){
        if(ModelEngineAPI.getModeledEntity(modeled.getBase().getUUID())==modeled)ModelEngineAPI.removeModeledEntity(modeled.getBase().getUUID());
        if(!modeled.isDestroyed())modeled.destroy();
    }
}
