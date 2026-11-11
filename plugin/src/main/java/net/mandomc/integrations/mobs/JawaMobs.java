package net.mandomc.integrations.mobs;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Mythic owns threat and attack scheduling; group retaliation only adds the actual attacker. */
public final class JawaMobs {
    private final MythicMobAccess mobs = new MythicMobAccess();
    private final Object api;
    private final Method active, table, adapt, gain;
    public JawaMobs() throws ReflectiveOperationException {
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        var loader = vendor.getClass().getClassLoader();
        api = vendor.getClass().getMethod("getAPIHelper").invoke(vendor);
        active = api.getClass().getMethod("getMythicMobInstance", Entity.class);
        table = Class.forName("io.lumine.mythic.core.mobs.ActiveMob", true, loader).getMethod("getThreatTable");
        adapt = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter", true, loader).getMethod("adapt", Entity.class);
        gain = table.getReturnType().getMethod("threatGain", adapt.getReturnType(), double.class);
    }
    public MythicMobAccess access() { return mobs; }
    public void retaliate(LivingEntity jawa, LivingEntity attacker) {
        try {
            Object mob = active.invoke(api, jawa);
            if (mob != null) gain.invoke(table.invoke(mob), adapt.invoke(null, attacker), 100.0);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot set Jawa retaliation", error); }
    }
}
