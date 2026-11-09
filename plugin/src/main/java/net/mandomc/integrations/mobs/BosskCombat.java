package net.mandomc.integrations.mobs;

import java.util.*;
import java.util.function.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.gameplay.items.ItemCatalog;

/** Mythic death loot and the confirmed, uncancelled swipe-hit boundary. */
public final class BosskCombat implements Listener {
    @SuppressWarnings("unchecked")
    public BosskCombat(JavaPlugin plugin,ItemCatalog items,Function<Entity,String> mobType,Predicate<Location> allowed)
            throws ReflectiveOperationException {
        var vendor=Bukkit.getPluginManager().getPlugin("MythicMobs");var loader=vendor.getClass().getClassLoader();
        var death=(Class<? extends Event>)Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",true,loader);
        var deathType=death.getMethod("getMobType");var name=deathType.getReturnType().getMethod("getInternalName");var drops=death.getMethod("getDrops");
        var api=vendor.getClass().getMethod("getAPIHelper").invoke(vendor);var active=api.getClass().getMethod("getMythicMobInstance",Entity.class);
        var activeClass=Class.forName("io.lumine.mythic.core.mobs.ActiveMob",true,loader);var registry=activeClass.getMethod("getAuraRegistry");var hasAura=registry.getReturnType().getMethod("hasAura",String.class);
        Bukkit.getPluginManager().registerEvent(death,this,EventPriority.HIGH,(l,e)->{
            try {if("Bossk".equals(name.invoke(deathType.invoke(e)))){var item=items.create("dioxis_grenade");if(item==null)throw new IllegalStateException("Missing dioxis grenade item");item.setAmount(ThreadLocalRandom.current().nextInt(1,4));((List<ItemStack>)drops.invoke(e)).add(item);}}
            catch(ReflectiveOperationException|RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bossk grenade drop failed",ex);}
        },plugin);
        Bukkit.getPluginManager().registerEvent(EntityDamageByEntityEvent.class,this,EventPriority.MONITOR,(l,e)->{
            if(!(e instanceof EntityDamageByEntityEvent hit))return;
            if(!(hit.getEntity() instanceof Player victim)||!(hit.getDamager() instanceof LivingEntity boss)
                    ||hit.getFinalDamage()<=0||!"Bossk".equals(mobType.apply(boss)))return;
            try {
                var mob=active.invoke(api,boss);
                if(mob==null||!(boolean)hasAura.invoke(registry.invoke(mob),"BosskConnectingSwipe"))return;
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(hit.isCancelled()||victim.isDead()||!victim.isValid()||!boss.isValid()||victim.getWorld()!=boss.getWorld()
                            ||!allowed.test(victim.getLocation())||!allowed.test(boss.getLocation()))return;
                    var away=victim.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
                    if(away.lengthSquared()<0.001)away=boss.getLocation().getDirection().setY(0);
                    victim.setVelocity(away.normalize().multiply(1.5).setY(0.5));
                });
            }catch(ReflectiveOperationException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bossk swipe knockback failed",ex);}
        },plugin,true);
    }
}
