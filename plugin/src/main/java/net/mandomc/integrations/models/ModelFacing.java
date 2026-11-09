package net.mandomc.integrations.models;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Synchronizes native and rendered body/head rotation before attack poses. */
public final class ModelFacing {
    private final Method modeled,bodyYaw,headYaw,headPitch,readBodyYaw,base,controller,nativeBodyYaw,nativeHeadYaw,nativePitch;
    public ModelFacing() throws ReflectiveOperationException {
        var plugin=Bukkit.getPluginManager().getPlugin("ModelEngine");
        if(plugin==null || !plugin.isEnabled())throw new IllegalStateException("Hostile facing requires ModelEngine");
        var loader=plugin.getClass().getClassLoader();
        modeled=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,loader).getMethod("getModeledEntity",Entity.class);
        var type=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,loader);
        readBodyYaw=type.getMethod("getYBodyRot");bodyYaw=type.getMethod("setYBodyRotImmediately",float.class);
        headYaw=type.getMethod("setYHeadRotImmediately",float.class);headPitch=type.getMethod("setXHeadRotImmediately",float.class);base=type.getMethod("getBase");
        controller=Class.forName("com.ticxo.modelengine.api.entity.BaseEntity",true,loader).getMethod("getBodyRotationController");
        var nativeType=Class.forName("com.ticxo.modelengine.api.nms.entity.wrapper.BodyRotationController",true,loader);
        nativeBodyYaw=nativeType.getMethod("setYBodyRot",float.class);nativeHeadYaw=nativeType.getMethod("setYHeadRot",float.class);nativePitch=nativeType.getMethod("setXHeadRot",float.class);
    }
    public void face(LivingEntity source,Location target,float maxStep) {
        var delta=target.toVector().subtract(source.getEyeLocation().toVector());
        if(delta.lengthSquared()<0.0001)return;
        var desired=source.getLocation().setDirection(delta);
        try {
            var model=modeled.invoke(null,source);
            float current=model==null?source.getYaw():((Number)readBodyYaw.invoke(model)).floatValue();
            float yaw=current+(float)Math.max(-maxStep,Math.min(maxStep,Math.IEEEremainder(desired.getYaw()-current,360)));
            source.setRotation(yaw,desired.getPitch());
            if(model==null)return;
            var nativeControl=controller.invoke(base.invoke(model));
            nativeBodyYaw.invoke(nativeControl,yaw);nativeHeadYaw.invoke(nativeControl,yaw);nativePitch.invoke(nativeControl,desired.getPitch());
            bodyYaw.invoke(model,yaw);headYaw.invoke(model,yaw);headPitch.invoke(model,desired.getPitch());
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot face hostile model",e);}
    }
}
