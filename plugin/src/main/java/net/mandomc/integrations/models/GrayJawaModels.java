package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.Color;
import org.bukkit.Location;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehavior;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorData;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorType;
import net.mandomc.gameplay.jawa.JawaLook;
import org.bukkit.entity.LivingEntity;
import java.util.*;
import java.util.function.Consumer;

/** Repairs only an anchored Jawa's gray rig; never removes another feature's models. */
public final class GrayJawaModels {
    private static final String MODEL="jawa_black";
    private record Retry(int failures,long next) { }
    private final Map<UUID,Retry> retries=new HashMap<>();
    private final Set<UUID> observed=new HashSet<>();
    private final Map<UUID,ActiveModel> looking = new HashMap<>();
    private final Consumer<String> log;
    public GrayJawaModels(Consumer<String> log){this.log=log;}
    public String audit(LivingEntity entity){
        var modeled=ModelEngineAPI.getModeledEntity(entity);
        var active=modeled==null?null:modeled.getModel(MODEL).orElse(null);
        var face=active==null?null:active.getBone("face").orElse(null);
        var head=active==null?null:active.getBone("head").orElse(null);
        var driver=head==null?null:head.getImmutableBoneBehaviors().get(BoneBehaviorTypes.HEAD);
        return "uuid="+entity.getUniqueId()+" models="+(modeled==null?"none":modeled.getModels().keySet())
                +" baseVisible="+(modeled==null||modeled.isBaseEntityVisible())+" initialized="+(modeled!=null&&modeled.isInitialized())
                +" grayLive="+(active!=null&&!active.isDestroyed()&&!active.isRemoved())
                +" eyes="+(face==null?"missing":Integer.toHexString(face.getDefaultTint().asRGB())+"/"+face.getBlockLight()+"/"+face.getSkyLight())
                +" headTracking="+(driver!=null&&!driver.isHidden()
                        &&head.getBoneBehavior(BoneBehaviorTypes.HEAD).orElseThrow().isLocal());
    }
    public boolean ensure(LivingEntity entity,long tick){
        if(!entity.isValid()||entity.isDead())return false;
        if(observed.add(entity.getUniqueId()))log.accept("Gray Jawa model before reconciliation: "+audit(entity));
        var retry=retries.get(entity.getUniqueId());if(retry!=null&&tick<retry.next())return false;
        try {
            if(ModelEngineAPI.getBlueprint(MODEL)==null)return pending(entity,tick);
            var modeled=ModelEngineAPI.getModeledEntity(entity);
            if(modeled==null||modeled.isDestroyed())modeled=ModelEngineAPI.getOrCreateModeledEntity(entity);
            var active=modeled.getModel(MODEL).orElse(null);
            boolean attached=false;
            if(active==null||active.isDestroyed()||active.isRemoved()){
                // A foreign model means another owner is using this entity: do not take it over.
                if(modeled.getModels().keySet().stream().anyMatch(id->!id.equals(MODEL)))return pending(entity,tick);
                if(active!=null)modeled.removeModel(MODEL);
                active=ModelEngineAPI.createActiveModel(MODEL);modeled.addModel(active,true);attached=true;
            }
            var face=active.getBone("face").orElse(null);
            if(face==null)return pending(entity,tick);
            face.setDefaultTint(Color.fromRGB(0xFF0000));face.setBlockLight(15);face.setSkyLight(15);
            if (looking.get(entity.getUniqueId()) != active) {
                var head = active.getBone("head").orElseThrow();
                head.removeBoneBehavior(BoneBehaviorTypes.HEAD);
                addHead(head, BoneBehaviorTypes.HEAD);
                refreshChildren(head);
                modeled.setModelRotationLocked(true);
                active.setModelRotationLocked(true);
                active.setLockYaw(true);
                active.setLockPitch(true);
                active.setLockedYBodyRot(entity.getYaw());
                active.setLockedYHeadRot(entity.getYaw());
                active.setLockedXHeadRot(0);
                modeled.setYBodyRotImmediately(entity.getYaw());
                modeled.setYHeadRotImmediately(entity.getYaw());
                modeled.setXHeadRotImmediately(0);
                looking.put(entity.getUniqueId(), active);
            }
            modeled.setSaved(true);
            // Hide the pig only after a real complete rig exists, never as a substitute for repair.
            modeled.setBaseEntityVisible(false);
            if(attached)active.getAnimationHandler().playAnimation("idle",0,0,1,true);
            if(attached||retry!=null)log.accept("Gray Jawa model reconciled: "+audit(entity));
            retries.remove(entity.getUniqueId());return true;
        }catch(RuntimeException failure){return pending(entity,tick);}
    }
    private static <T extends BoneBehavior> void addHead(ModelBone bone, BoneBehaviorType<T> type) {
        bone.addBoneBehavior(type.getBehaviorProvider().create(bone, type,
                new BoneBehaviorData(Map.of("local", true, "inherited", true))));
    }

    private static void refreshChildren(ModelBone parent) {
        for (var child : parent.getChildren().values()) {
            child.forBehaviors(behavior -> behavior.onParentSwap(parent));
            refreshChildren(child);
        }
    }

    /** Only the existing anchored gray owner calls this; brown conversation/combat facing stays separate. */
    public void look(LivingEntity entity, Location home, Location target) {
        var modeled = ModelEngineAPI.getModeledEntity(entity);
        var active = looking.get(entity.getUniqueId());
        if (modeled == null || active == null || active.isDestroyed() || active.isRemoved()
                || modeled.getModel(MODEL).orElse(null) != active) return;
        // Reattaching a main hitbox changes the pig's native eye height; the rendered head stays fixed.
        float headHeight = active.getBone("head").orElseThrow().getBlueprintBone().getGlobalPosition().y
                * active.getScale().y();
        var origin = entity.getLocation().add(0, headHeight, 0);
        var desired = target == null ? home : home.clone().setDirection(
                target.toVector().subtract(origin.toVector()));
        var pose = JawaLook.step(active.getYBodyRot(), active.getYHeadRot(), active.getXHeadRot(),
                desired.getYaw(), desired.getPitch(), target != null);
        entity.setRotation(pose.body(), pose.pitch());
        var controller = modeled.getBase().getBodyRotationController();
        controller.setYBodyRot(pose.body());
        controller.setYHeadRot(pose.head());
        controller.setXHeadRot(pose.pitch());
        modeled.setYBodyRotImmediately(pose.body());
        modeled.setYHeadRotImmediately(pose.head());
        modeled.setXHeadRotImmediately(pose.pitch());
        active.setLockedYBodyRot(pose.body());
        active.setLockedYHeadRot(pose.head());
        active.setLockedXHeadRot(pose.pitch());
    }

    private boolean pending(LivingEntity entity,long tick){
        int failures=Math.min(8,Optional.ofNullable(retries.get(entity.getUniqueId())).map(Retry::failures).orElse(0)+1);
        long delay=failures==8?1200:Math.min(200,5L<<failures);
        retries.put(entity.getUniqueId(),new Retry(failures,tick+delay));
        if(failures==8)log.accept("Gray Jawa model unavailable; bounded retry cooling down: "+audit(entity));
        return false;
    }
    public void unloaded(UUID id){retries.remove(id);observed.remove(id);looking.remove(id);}
    public void close(){retries.clear();observed.clear();looking.clear();}
}
