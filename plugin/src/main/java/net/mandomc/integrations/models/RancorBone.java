package net.mandomc.integrations.models;
import java.lang.reflect.Method;import java.util.*;import org.bukkit.*;import org.bukkit.entity.*;
/** Current-pose hand/foot position, including models with no nearby viewers. */
public final class RancorBone {
 private final Method modeled,models,bone,parent,tick,location;
 public RancorBone() throws ReflectiveOperationException {
  var loader=Bukkit.getPluginManager().getPlugin("ModelEngine").getClass().getClassLoader();modeled=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,loader).getMethod("getModeledEntity",Entity.class);models=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,loader).getMethod("getModels");bone=Class.forName("com.ticxo.modelengine.api.model.ActiveModel",true,loader).getMethod("getBone",String.class);var type=Class.forName("com.ticxo.modelengine.api.model.bone.ModelBone",true,loader);parent=type.getMethod("getParent");tick=type.getMethod("tick");location=type.getMethod("getLocation");
 }
 public Optional<Location> position(LivingEntity actor,String name){try{var entity=modeled.invoke(null,actor);if(entity==null)return Optional.empty();var model=((Map<?,?>)models.invoke(entity)).get("rancor");if(model==null)return Optional.empty();var part=((Optional<?>)bone.invoke(model,name)).orElse(null);if(part==null)return Optional.empty();var chain=new ArrayList<Object>();for(Object current=part;current!=null&&chain.size()<16;current=parent.invoke(current))chain.add(current);Collections.reverse(chain);for(Object current:chain)tick.invoke(current);var point=(Location)location.invoke(part);return point.getWorld()==actor.getWorld()&&point.distanceSquared(actor.getLocation())<1024?Optional.of(point):Optional.empty();}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot resolve animated Rancor limb",e);}}
}
