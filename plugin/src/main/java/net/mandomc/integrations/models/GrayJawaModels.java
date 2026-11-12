package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.Color;
import org.bukkit.entity.LivingEntity;
import java.util.*;
import java.util.function.Consumer;

/** Repairs only an anchored Jawa's gray rig; never removes another feature's models. */
public final class GrayJawaModels {
    private static final String MODEL="jawa_black";
    private record Retry(int failures,long next) { }
    private final Map<UUID,Retry> retries=new HashMap<>();
    private final Set<UUID> observed=new HashSet<>();
    private final Consumer<String> log;
    public GrayJawaModels(Consumer<String> log){this.log=log;}
    public String audit(LivingEntity entity){
        var modeled=ModelEngineAPI.getModeledEntity(entity);
        var active=modeled==null?null:modeled.getModel(MODEL).orElse(null);
        var face=active==null?null:active.getBone("face").orElse(null);
        return "uuid="+entity.getUniqueId()+" models="+(modeled==null?"none":modeled.getModels().keySet())
                +" baseVisible="+(modeled==null||modeled.isBaseEntityVisible())+" initialized="+(modeled!=null&&modeled.isInitialized())
                +" grayLive="+(active!=null&&!active.isDestroyed()&&!active.isRemoved())
                +" eyes="+(face==null?"missing":Integer.toHexString(face.getDefaultTint().asRGB())+"/"+face.getBlockLight()+"/"+face.getSkyLight());
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
            modeled.setSaved(true);
            // Hide the pig only after a real complete rig exists, never as a substitute for repair.
            modeled.setBaseEntityVisible(false);
            if(attached)active.getAnimationHandler().playAnimation("idle",0,0,1,true);
            if(attached||retry!=null)log.accept("Gray Jawa model reconciled: "+audit(entity));
            retries.remove(entity.getUniqueId());return true;
        }catch(RuntimeException failure){return pending(entity,tick);}
    }
    private boolean pending(LivingEntity entity,long tick){
        int failures=Math.min(8,Optional.ofNullable(retries.get(entity.getUniqueId())).map(Retry::failures).orElse(0)+1);
        long delay=failures==8?1200:Math.min(200,5L<<failures);
        retries.put(entity.getUniqueId(),new Retry(failures,tick+delay));
        if(failures==8)log.accept("Gray Jawa model unavailable; bounded retry cooling down: "+audit(entity));
        return false;
    }
    public void unloaded(UUID id){retries.remove(id);observed.remove(id);}
    public void close(){retries.clear();observed.clear();}
}
