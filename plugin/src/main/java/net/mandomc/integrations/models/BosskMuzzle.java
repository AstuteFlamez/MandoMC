package net.mandomc.integrations.models;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Pinned ModelEngine boundary: animated rifle pivot plus its original Blockbench barrel-tip offset. */
public final class BosskMuzzle {
    private final Method modeled, models, bone, location, bones, tickBone;
    private final ModelFacing facing;
    private final Object local;
    public BosskMuzzle() throws ReflectiveOperationException {
        var vendor=Bukkit.getPluginManager().getPlugin("ModelEngine");
        if(vendor==null||!vendor.isEnabled())throw new IllegalStateException("Bossk requires ModelEngine");
        var loader=vendor.getClass().getClassLoader();
        modeled=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,loader).getMethod("getModeledEntity",Entity.class);
        var model=Class.forName("com.ticxo.modelengine.api.model.ActiveModel",true,loader);
        models=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,loader).getMethod("getModels");
        facing=new ModelFacing();
        bone=model.getMethod("getBone",String.class);
        bones=model.getMethod("getBones");
        tickBone=Class.forName("com.ticxo.modelengine.api.model.bone.ModelBone",true,loader).getMethod("tick");
        var mode=Class.forName("com.ticxo.modelengine.api.utils.OffsetMode",true,loader);
        local=mode.getField("LOCAL").get(null);
        location=Class.forName("com.ticxo.modelengine.api.model.bone.ModelBone",true,loader)
                .getMethod("getLocation");
    }
    public Location position(LivingEntity caster) {
        try {
            var entity=modeled.invoke(null,caster);
            if(entity==null)throw new IllegalStateException("Bossk model is not attached");
            var model=((Map<?,?>)models.invoke(entity)).get("bossk");
            if(model==null)throw new IllegalStateException("Bossk active model is missing");
            // ModelEngine skips full bone transforms without viewers. Resolve the current animation
            // along the muzzle chain before gameplay uses it, even for a culled/unwatched boss.
            var chain=(Map<?,?>)bones.invoke(model);
            for(String name:java.util.List.of("body","torso","right_arm","rifle","bossk_muzzle")) {
                var part=chain.get(name);if(part!=null)tickBone.invoke(part);
            }
            var rifle=((Optional<?>)bone.invoke(model,"bossk_muzzle")).orElseThrow(()->new IllegalStateException("Bossk muzzle locator is missing"));
            var result=(Location)location.invoke(rifle);
            if(result.getWorld()!=caster.getWorld()||result.distanceSquared(caster.getLocation())>16)
                throw new IllegalStateException("Bossk muzzle transform is out of bounds");
            return result;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot resolve animated Bossk muzzle",e);}
    }
    public void face(LivingEntity caster,Location target) { facing.face(caster,target,30); }
}
