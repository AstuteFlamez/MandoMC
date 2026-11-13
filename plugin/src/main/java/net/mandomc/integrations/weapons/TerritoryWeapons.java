package net.mandomc.integrations.weapons;

import net.mandomc.gameplay.territory.TerritoryService;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Supported pinned WeaponMechanics event hooks replace WorldGuard's shoot/damage flags. */
public final class TerritoryWeapons implements Listener {
    @SuppressWarnings("unchecked")
    public TerritoryWeapons(JavaPlugin plugin, TerritoryService territories) throws ReflectiveOperationException {
        var vendor=plugin.getServer().getPluginManager().getPlugin("WeaponMechanics");
        if(vendor==null)throw new IllegalStateException("Native territory weapon protection requires WeaponMechanics");
        var loader=vendor.getClass().getClassLoader();
        for(String name:new String[]{"WeaponPreShootEvent","WeaponDamageEntityEvent"}) {
            var type=(Class<? extends Event>)Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents."+name,true,loader);
            var shooter=type.getMethod("getShooter");
            var victim=name.equals("WeaponDamageEntityEvent")?type.getMethod("getVictim"):null;
            plugin.getServer().getPluginManager().registerEvent(type,this,EventPriority.HIGHEST,(listener,event)->{
                try {
                    Object source=shooter.invoke(event);
                    Object target=victim==null?null:victim.invoke(event);
                    var clans=plugin.getServer().getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);
                    if((clans!=null&&source instanceof Entity shooterEntity&&target instanceof Entity targetEntity
                            &&clans.friendly(shooterEntity,targetEntity))
                            ||(source instanceof Entity entity&&!territories.combat(entity.getLocation()))
                            ||(target instanceof Entity victimEntity&&!territories.combat(victimEntity.getLocation())))
                        ((Cancellable)event).setCancelled(true);
                }catch(ReflectiveOperationException error){
                    ((Cancellable)event).setCancelled(true);
                    throw new org.bukkit.event.EventException(error);
                }
            },plugin,true);
        }
    }
}
