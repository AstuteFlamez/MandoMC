package net.mandomc.integrations.mobs;

import java.util.List;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.trophies.RancorTrophies;
import net.mandomc.integrations.regions.NativeTerritoryAccess;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** The vendor's actual death-loot list is the single drop pipeline; cleanup removals are not deaths. */
public final class RancorTrophyDrops implements Listener {
    @SuppressWarnings("unchecked")
    public RancorTrophyDrops(JavaPlugin plugin,ItemCatalog items)throws ReflectiveOperationException {
        try{new RancorTrophies(plugin,items,(player,location)->{
            var territory=NativeTerritoryAccess.current();
            return territory!=null&&(territory.edits(location)||player.getGameMode()==GameMode.CREATIVE&&player.hasPermission("mandomc.territory.edit"));
        });}catch(Exception error){throw new IllegalStateException("Cannot initialize Rancor trophy custody",error);}
        var vendor=Bukkit.getPluginManager().getPlugin("MythicMobs");
        var type=(Class<? extends Event>)Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",true,vendor.getClass().getClassLoader());
        var mobType=type.getMethod("getMobType");var name=mobType.getReturnType().getMethod("getInternalName");
        var entity=type.getMethod("getEntity");var drops=type.getMethod("getDrops");
        var dropped=new NamespacedKey(plugin,"rancor_skull_drop_v1");
        Bukkit.getPluginManager().registerEvent(type,this,EventPriority.HIGH,(listener,event)->{
            try{
                if(!"Rancor".equals(name.invoke(mobType.invoke(event)))||!(entity.invoke(event) instanceof LivingEntity boss)
                        ||(!boss.isDead()&&boss.getHealth()>0)||boss.getPersistentDataContainer().has(dropped))return;
                var item=items.create(RancorTrophies.ITEM);if(item==null)throw new IllegalStateException("Missing Rancor Skull factory");item.setAmount(1);
                var meta=item.getItemMeta();meta.setMaxStackSize(1);item.setItemMeta(meta);
                ((List<ItemStack>)drops.invoke(event)).add(item);boss.getPersistentDataContainer().set(dropped,PersistentDataType.BYTE,(byte)1);
            }catch(ReflectiveOperationException|RuntimeException failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Rancor Skull death loot failed",failure);}
        },plugin);
    }
}
