package net.mandomc.verification;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.mandomc.bootstrap.MandoPlugin;

/** Temporary catalog/native weapon and vendor death-drop integration checks. */
public final class GaffiProbe extends JavaPlugin {
    private final List<Entity> spawned = new ArrayList<>();
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try { verify(); } catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"GAFFI PROBE FAILED",e);}
            finally { spawned.forEach(Entity::remove); }
        },120);
    }
    private void verify() throws Exception {
        var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        var item=plugin.items().create("gaffi_stick");
        check(item!=null && item.getType()==Material.IRON_SWORD,"gaffi uses native iron sword");
        check("gaffi_stick".equals(plugin.items().identify(item)),"gaffi catalog identity survives generation");
        check(!plugin.rules().banned(item),"custom gaffi is usable under survival restrictions");
        check(plugin.rules().banned(new ItemStack(Material.IRON_SWORD)),"ordinary iron swords remain restricted");
        check(item.getItemMeta().getItemModel().toString().equals("mandomc:melee/gaffi_stick"),"gaffi selects its shipped model");
        var vanilla=new ItemStack(Material.IRON_SWORD);
        check(item.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)
                .equals(vanilla.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)),
                "native attack damage and speed match iron sword exactly");
        check(item.getType().getMaxDurability()==250,"native iron sword durability retained");
        var world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for(String type:List.of("TuskenRaiderMelee","TuskenRaiderSniper","Bantha")) {
            var entity=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob(type,new Location(world,600.5,301,600.5));
            spawned.add(entity);entity.setAI(false);entity.setGravity(false);
            var mob=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
            int count=0;
            for(int i=0;i<2000;i++) {
                var event=new MythicMobDeathEvent(mob,null,new ArrayList<>());
                Bukkit.getPluginManager().callEvent(event);
                if(event.getDrops().size()>1)throw new AssertionError("Duplicate weapon drop");
                for(var drop:event.getDrops()) {
                    count++;
                    if(type.equals("TuskenRaiderMelee")) {
                        if(!"gaffi_stick".equals(plugin.items().identify(drop)))throw new AssertionError("Wrong melee drop");
                    } else if(type.equals("TuskenRaiderSniper")) {
                        if(!"CyclerRifle".equals(me.deecaad.weaponmechanics.WeaponMechanicsAPI.getWeaponTitle(drop)))
                            throw new AssertionError("Wrong ranged drop");
                    } else throw new AssertionError("Wildlife received Tusken loot");
                }
            }
            check(type.equals("Bantha")?count==0:count>=130 && count<=270,
                    type+" drop distribution "+count+"/2000, at most one correct weapon per death");
        }
        getLogger().info("GAFFI PROBE PASSED: "+checks+" assertions");
    }
    private void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);checks++;getLogger().info("PASS: "+message);}
    @Override public void onDisable(){spawned.forEach(Entity::remove);}
}
