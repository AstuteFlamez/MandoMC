package net.mandomc.integrations.weapons;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

/** WeaponMechanics can apply a lethal health change with a generic native death source. */
public final class PlayerMobKills implements Listener,AutoCloseable {
    private record Hit(Player shooter,LivingEntity victim,Event event,long tick) { }
    private final Map<UUID,Hit> lethal=new HashMap<>();
    private final Method damage;
    public PlayerMobKills(JavaPlugin plugin)throws ReflectiveOperationException {
        var vendor=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("WeaponMechanics"));
        @SuppressWarnings("unchecked") var type=(Class<? extends Event>)Class.forName(
                "me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent",true,vendor.getClass().getClassLoader());
        var shooter=type.getMethod("getShooter");var victim=type.getMethod("getVictim");damage=type.getMethod("getFinalDamage");
        Bukkit.getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(listener,event)->{
            try {
                var target=(LivingEntity)victim.invoke(event);lethal.remove(target.getUniqueId());
                if(!(shooter.invoke(event) instanceof Player player)||((Number)damage.invoke(event)).doubleValue()<target.getHealth()+target.getAbsorptionAmount())return;
                var hit=new Hit(player,target,event,Bukkit.getCurrentTick());lethal.put(target.getUniqueId(),hit);
                Bukkit.getScheduler().runTask(plugin,()->lethal.remove(target.getUniqueId(),hit));
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot attribute player weapon kill",e);}
        },plugin,true);
    }
    public Player killer(LivingEntity victim) {
        var hit=lethal.remove(victim.getUniqueId());
        if(hit==null||hit.tick()!=Bukkit.getCurrentTick()||((Cancellable)hit.event()).isCancelled())return null;
        try{return ((Number)damage.invoke(hit.event())).doubleValue()>0?hit.shooter():null;}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot validate player weapon kill",e);}
    }
    @Override public void close(){lethal.clear();HandlerList.unregisterAll(this);}
}
