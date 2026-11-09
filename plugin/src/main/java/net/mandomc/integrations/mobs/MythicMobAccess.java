package net.mandomc.integrations.mobs;

import java.lang.reflect.Method;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Public API boundary for the pinned MythicMobs version; Mythic owns AI, level and entity lifecycle. */
public final class MythicMobAccess {
    public static final Set<String> TYPES = Set.of("TuskenRaiderSniper", "TuskenRaiderMelee", "Bantha", "Dewback", "Massiff");
    private final Object api;
    private final Method spawn;
    private final Method active;
    private final Method type;
    private final Method level;
    private final Method remove;
    private final Method persist;
    private final Object never;

    public MythicMobAccess() throws ReflectiveOperationException {
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (vendor == null || !vendor.isEnabled() || !Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            throw new IllegalStateException("Sandy mobs require MythicMobs and ModelEngine");
        }
        api = vendor.getClass().getMethod("getAPIHelper").invoke(vendor);
        spawn = api.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class);
        active = api.getClass().getMethod("getMythicMobInstance", Entity.class);
        var activeType = Class.forName("io.lumine.mythic.core.mobs.ActiveMob", true, vendor.getClass().getClassLoader());
        type = activeType.getMethod("getMobType");
        level = activeType.getMethod("getLevel");
        remove = activeType.getMethod("remove");
        var mode=Class.forName("io.lumine.mythic.core.mobs.DespawnMode",true,vendor.getClass().getClassLoader());
        persist=activeType.getMethod("setDespawnMode",mode);never=mode.getField("NEVER").get(null);
        var definition = api.getClass().getMethod("getMythicMob", String.class);
        for (String id : TYPES) {
            if (definition.invoke(api, id) == null) throw new IllegalStateException("Missing MythicMobs definition: " + id);
        }
    }

    public LivingEntity spawn(String id, Location location, int mobLevel) {
        try {
            return (LivingEntity) spawn.invoke(api, id, location, mobLevel);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot spawn Mythic mob " + id, error);
        }
    }

    public String type(Entity entity) {
        try {
            Object mob = active.invoke(api, entity);
            return mob == null ? "" : (String) type.invoke(mob);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot identify Mythic mob", error);
        }
    }

    public int level(Entity entity) {
        try {
            Object mob = active.invoke(api, entity);
            return mob == null ? 1 : ((Number) level.invoke(mob)).intValue();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read Mythic mob level", error);
        }
    }

    public void persist(Entity entity) {
        try {var mob=active.invoke(api,entity);if(mob!=null)persist.invoke(mob,never);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot persist dungeon mob",error);}
    }

    public void remove(Entity entity) {
        try {
            Object mob = active.invoke(api, entity);
            if (mob != null) remove.invoke(mob);
            else entity.remove();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot remove Mythic mob", error);
        }
    }

    public boolean managed(Entity entity) {
        return TYPES.contains(type(entity));
    }
}
