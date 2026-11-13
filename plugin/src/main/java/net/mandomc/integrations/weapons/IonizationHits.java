package net.mandomc.integrations.weapons;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.jawa.JawaIonization;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

/** Correlate the ion projectile event with its actual uncancelled Bukkit damage and resulting health loss. */
public final class IonizationHits implements Listener,AutoCloseable {
    private record Pending(Player victim,LivingEntity shooter,Event vendorEvent,long tick,long epoch,double health) { }
    private final MandoPlugin plugin;
    private final JawaIonization effect;
    private final Map<UUID,Pending> pending=new HashMap<>();
    @SuppressWarnings("unchecked")
    public IonizationHits(MandoPlugin plugin,JawaIonization effect) throws ReflectiveOperationException {
        this.plugin=plugin;this.effect=effect;
        var wm=Bukkit.getPluginManager().getPlugin("WeaponMechanics");var loader=wm.getClass().getClassLoader();
        var type=(Class<? extends Event>)Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent",true,loader);
        var title=type.getMethod("getWeaponTitle");var source=type.getMethod("getSource");var victim=type.getMethod("getVictim");var shooter=type.getMethod("getShooter");var damage=type.getMethod("getFinalDamage");
        var projectile=Class.forName("me.deecaad.weaponmechanics.weapon.damage.ProjectileDamageSource",true,loader);
        Bukkit.getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(listener,event)->{
            try {
                if(!"Jawa_Ionization_Blaster".equals(title.invoke(event)) || !projectile.isInstance(source.invoke(event)) || ((Number)damage.invoke(event)).doubleValue()<=0)return;
                if(!(victim.invoke(event) instanceof Player player) || !(shooter.invoke(event) instanceof LivingEntity attacker))return;
                if(!plugin.territories().combat(player.getLocation()) || !plugin.territories().combat(attacker.getLocation()))return;
                var hit=new Pending(player,attacker,event,Bukkit.getCurrentTick(),effect.epoch(player),player.getHealth()+player.getAbsorptionAmount());pending.put(player.getUniqueId(),hit);
                Bukkit.getScheduler().runTask(plugin,()->pending.remove(player.getUniqueId(),hit));
            }catch(ReflectiveOperationException error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot classify ionization hit",error);}
        },plugin,true);
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof Player player) || event.getFinalDamage()<=0 || event.getCause()!=EntityDamageEvent.DamageCause.PROJECTILE)return;
        var hit=pending.get(player.getUniqueId());if(hit==null || hit.tick()!=Bukkit.getCurrentTick())return;
        var source=event.getDamageSource().getCausingEntity();if(source==null)source=event.getDamager();
        if(source==null || !source.getUniqueId().equals(hit.shooter().getUniqueId()))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || ((Cancellable)hit.vendorEvent()).isCancelled() || event.getFinalDamage()<=0
                    || !player.isOnline() || player.isDead() || effect.epoch(player)!=hit.epoch()
                    || player.getHealth()+player.getAbsorptionAmount()>=hit.health()
                    || !plugin.territories().combat(player.getLocation()) || !plugin.territories().combat(hit.shooter().getLocation()))return;
            effect.hit(player,hit.tick());
        });
    }
    @Override public void close(){HandlerList.unregisterAll(this);pending.clear();}
}
