package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.mandomc.gameplay.decor.DecorDefinition;
import org.bukkit.entity.ArmorStand;

/** Renders existing rigs only; never registers player-vehicle ownership, controllers or combat AI. */
public final class DecorModel implements AutoCloseable {
    private final ModeledEntity modeled;
    private final ActiveModel active;
    public DecorModel(ArmorStand base,DecorDefinition definition){
        if(ModelEngineAPI.getBlueprint(definition.model())==null)throw new IllegalStateException("Missing decor blueprint "+definition.model());
        modeled=ModelEngineAPI.getOrCreateModeledEntity(base);modeled.setSaved(false);modeled.setBaseEntityVisible(false);
        active=ModelEngineAPI.createActiveModel(definition.model());active.setScale(definition.scale());
        // This flag sends an invisible INTERACTION target to clients; it does not show the native base.
        active.setCanHurt(false);active.setHitboxVisible(definition.npc());active.setMainHitbox(false);active.setRenderFire(false);
        modeled.addModel(active,false);
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
    public ModeledEntity modeled(){return modeled;}
    @Override public void close(){modeled.destroy();}
}
