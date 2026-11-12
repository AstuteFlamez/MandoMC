package net.mandomc.gameplay.decor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

/** Exact, immutable placement facts; runtime model/entity state has a separate single owner. */
public record DecorDefinition(String id,String name,String type,String model,double scale,String mode,boolean openDoors,
        String world,double x,double y,double z,float yaw,float pitch,List<String> animations,List<String> voices,List<String> dialogue) {
    public DecorDefinition {
        if(id==null||!id.matches("[a-z0-9_]+")||name==null||name.isBlank()||NamespacedKey.fromString(world)==null
                ||!Double.isFinite(scale)||scale<=0||scale>4||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)
                ||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Invalid decorative placement "+id);
        animations=List.copyOf(animations);voices=List.copyOf(voices);dialogue=List.copyOf(dialogue);
        if(voices.stream().anyMatch(sound->NamespacedKey.fromString(sound)==null))throw new IllegalArgumentException("Invalid voice for "+id);
    }
    private static double number(Map<?,?> map,String key){
        var value=map.get(key);if(!(value instanceof Number n))throw new IllegalArgumentException("Missing numeric "+key);return n.doubleValue();
    }
    private static String text(Map<?,?> map,String key){return Objects.requireNonNull(map.get(key),"Missing "+key).toString();}
    private static List<String> list(Map<?,?> map,String key){return map.get(key) instanceof List<?> values?values.stream().map(Object::toString).toList():List.of();}
    public static DecorDefinition read(Map<?,?> data){
        var at=(Map<?,?>)Objects.requireNonNull(data.get("location"));
        return new DecorDefinition(text(data,"id"),text(data,"name"),text(data,"type"),text(data,"model"),number(data,"scale"),text(data,"mode"),Boolean.TRUE.equals(data.get("open-doors")),
                text(at,"world"),number(at,"x"),number(at,"y"),number(at,"z"),(float)number(at,"yaw"),(float)number(at,"pitch"),list(data,"animations"),list(data,"voices"),list(data,"dialogue"));
    }
    public boolean npc(){return !dialogue.isEmpty();}
    public Location location(){
        var target=Bukkit.getWorld(NamespacedKey.fromString(world));
        if(target==null)throw new IllegalStateException("Missing decorative world "+world);
        return new Location(target,x,y,z,yaw,pitch);
    }
}
