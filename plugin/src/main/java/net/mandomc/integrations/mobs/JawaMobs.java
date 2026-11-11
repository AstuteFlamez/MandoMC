package net.mandomc.integrations.mobs;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Isolates Mythic threat and attack casts; group retaliation only adds the actual attacker. */
public final class JawaMobs {
    private final MythicMobAccess mobs = new MythicMobAccess();
    private final Object api;
    private final Method active, table, adapt, gain, cast, top, bukkit;
    public JawaMobs() throws ReflectiveOperationException {
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        var loader = vendor.getClass().getClassLoader();
        api = vendor.getClass().getMethod("getAPIHelper").invoke(vendor);
        cast=api.getClass().getMethod("castSkill",Entity.class,String.class,org.bukkit.Location.class,java.util.Collection.class,java.util.Collection.class,float.class);
        active = api.getClass().getMethod("getMythicMobInstance", Entity.class);
        table = Class.forName("io.lumine.mythic.core.mobs.ActiveMob", true, loader).getMethod("getThreatTable");
        adapt = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter", true, loader).getMethod("adapt", Entity.class);
        top=table.getReturnType().getMethod("getTopThreatHolder");
        bukkit=adapt.getReturnType().getMethod("getBukkitEntity");
        gain = table.getReturnType().getMethod("threatGain", adapt.getReturnType(), double.class);
    }
    /** Mythic's threat target can be present while the host pig's vanilla target is null. */
    public LivingEntity target(org.bukkit.entity.Mob jawa) {
        try {
            Object mob=active.invoke(api,jawa);
            Object selected=mob==null?null:top.invoke(table.invoke(mob));
            return selected!=null && bukkit.invoke(selected) instanceof LivingEntity living?living:jawa.getTarget();
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot read Jawa threat target",error);}
    }
    public boolean attack(LivingEntity jawa,LivingEntity target) {
        try {return (Boolean)cast.invoke(api,jawa,"JawaAttack",jawa.getLocation(),java.util.List.of(target),java.util.List.of(),1f);}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot cast Jawa attack",error);}
    }
    public MythicMobAccess access() { return mobs; }
    public void retaliate(LivingEntity jawa, LivingEntity attacker) {
        try {
            Object mob = active.invoke(api, jawa);
            if (mob != null) gain.invoke(table.invoke(mob), adapt.invoke(null, attacker), 100.0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot set Jawa retaliation", error); }
    }
}
