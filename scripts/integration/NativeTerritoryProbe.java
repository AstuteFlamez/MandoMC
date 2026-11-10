package net.mandomc.verification;

import net.mandomc.gameplay.territory.*;
import net.mandomc.integrations.regions.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Main-thread action probes construct events but never mutate terrain or damage real players. */
public final class NativeTerritoryProbe extends JavaPlugin {
    private int checks;
    private void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);checks++;}
    private String plain(Component c){String s=c instanceof TextComponent t?t.content():"";for(var child:c.children())s+=plain(child);return s;}
    private static Object defaultValue(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;return null;}
    private static final class Fixture {
        Location location;GameMode mode=GameMode.SURVIVAL;boolean edit;UUID id=UUID.randomUUID();
        List<Component> messages=new ArrayList<>();List<Title> titles=new ArrayList<>();Player player;
        Fixture(Location location){this.location=location;player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->{
            switch(m.getName()){
                case "getLocation":return this.location.clone();case "getWorld":return this.location.getWorld();case "getGameMode":return mode;
                case "getUniqueId":return id;case "getName":return "NativeTerritoryFixture";case "isOnline":return true;case "getPassengers":return List.of();
                case "hasPermission":return a[0].equals("mandomc.territory.edit")?edit:true;case "isPermissionSet":return true;
                case "sendMessage":if(a[0] instanceof Component c)messages.add(c);return null;
                case "showTitle":titles.add((Title)a[0]);return null;case "hashCode":return id.hashCode();case "equals":return p==a[0];case "toString":return "NativeTerritoryFixture";
            }return defaultValue(m.getReturnType());
        });}
    }
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"NATIVE TERRITORY FAILED",error);}},60);}
    private void run() throws Exception {
        var owner=Bukkit.getPluginManager().getPlugin("MandoMC");check(owner!=null&&owner.isEnabled(),"MandoMC enabled");
        check(Bukkit.getPluginManager().getPlugin("WorldGuard")==null,"WorldGuard absent");
        check(Bukkit.getPluginManager().getPlugin("Factions")==null&&Bukkit.getPluginManager().getPlugin("FactionsUUID")==null,"Factions absent");
        var service=Bukkit.getServicesManager().load(TerritoryService.class);check(service!=null,"Native authority registered");
        var nativeListeners=Arrays.stream(HandlerList.getRegisteredListeners(owner).toArray(new org.bukkit.plugin.RegisteredListener[0])).map(x->x.getListener().getClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        check(nativeListeners.contains("TerritoryProtection")&&nativeListeners.contains("TerritoryService")&&nativeListeners.contains("TerritoryWeapons"),"Live listeners and weapon boundary registered");
        var mando=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));var batuu=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));var hoth=Bukkit.getWorld(NamespacedKey.minecraft("hoth"));
        check(mando!=null&&batuu!=null&&hoth!=null,"Worlds retained");check(mando.getPVP(),"Mandalore combat enabled outside native safezone");
        Location safe=new Location(mando,-18,70,-192),war=new Location(mando,0,8,0),wild=new Location(mando,-69,76,-188);
        check(service.zone(safe)==TerritoryPolicy.Zone.SAFEZONE,"Spawn safe");check(service.zone(war)==TerritoryPolicy.Zone.WARZONE,"Caves war");check(service.zone(wild)==TerritoryPolicy.Zone.WILDERNESS,"Nearby ruin wilderness");
        for(int x=-42;x<=6;x++)for(int z=-211;z<=-173;z++)
            check(service.zone(new Location(mando,x,70,z))==TerritoryPolicy.Zone.SAFEZONE,"Entire spawn footprint "+x+","+z);
        new NativeArrivalProtection().requireProtected(new Location(batuu,-.25,93,-16.25));checks++;
        check(!new SaberProtection().allowed(safe)&&new SaberProtection().allowed(war),"Saber native combat");
        check(!new VehicleProtection().blocks(war)&&new VehicleProtection().blocks(wild),"Cannon terrain native");
        check(!new MobProtection().allowed(safe)&&!new MobProtection().allowed(new Location(hoth,-148,27,141)),"Spawn migration");
        var f=new Fixture(safe);var guard=new TerritoryProtection(service);
        var before=safe.getBlock().getBlockData().getAsString();
        var breaking=new BlockBreakEvent(safe.getBlock(),f.player);guard.breakBlock(breaking);check(breaking.isCancelled(),"Safe edit denied");
        f.edit=true;breaking=new BlockBreakEvent(safe.getBlock(),f.player);guard.breakBlock(breaking);check(breaking.isCancelled(),"Permission alone cannot bypass Survival");
        f.mode=GameMode.CREATIVE;breaking=new BlockBreakEvent(safe.getBlock(),f.player);guard.breakBlock(breaking);check(!breaking.isCancelled(),"Explicit Creative staff maintenance");
        f.edit=false;breaking=new BlockBreakEvent(safe.getBlock(),f.player);guard.breakBlock(breaking);check(breaking.isCancelled(),"Creative alone cannot edit");
        f.mode=GameMode.SURVIVAL;
        breaking=new BlockBreakEvent(war.getBlock(),f.player);guard.breakBlock(breaking);check(breaking.isCancelled(),"Warzone terrain preserved");
        breaking=new BlockBreakEvent(wild.getBlock(),f.player);guard.breakBlock(breaking);check(!breaking.isCancelled(),"Wilderness edits allowed");
        breaking.setCancelled(true);guard.breakBlock(breaking);check(breaking.isCancelled(),"Existing cancellation preserved");
        var placed=new BlockPlaceEvent(safe.getBlock(),safe.getBlock().getState(),safe.getBlock().getRelative(BlockFace.DOWN),new ItemStack(Material.STONE),f.player,true,EquipmentSlot.HAND);guard.place(placed);check(placed.isCancelled(),"Placement denied");
        var damageSource=(DamageSource)Proxy.newProxyInstance(DamageSource.class.getClassLoader(),new Class[]{DamageSource.class},(p,m,a)->m.getName().equals("getCausingEntity")?null:defaultValue(m.getReturnType()));
        var damage=new EntityDamageEvent(f.player,EntityDamageEvent.DamageCause.FALL,damageSource,5);guard.damage(damage);check(damage.isCancelled(),"Safezone fall damage denied");
        f.location=war;damage=new EntityDamageEvent(f.player,EntityDamageEvent.DamageCause.FALL,damageSource,5);guard.damage(damage);check(!damage.isCancelled(),"Warzone damage allowed");
        var attacker=new Fixture(safe);damageSource=(DamageSource)Proxy.newProxyInstance(DamageSource.class.getClassLoader(),new Class[]{DamageSource.class},(p,m,a)->m.getName().equals("getCausingEntity")?attacker.player:defaultValue(m.getReturnType()));
        damage=new EntityDamageEvent(f.player,EntityDamageEvent.DamageCause.ENTITY_ATTACK,damageSource,5);guard.damage(damage);check(damage.isCancelled(),"Safezone attacker cannot hit out");
        var blast=new EntityExplodeEvent(f.player,war,new ArrayList<>(List.of(safe.getBlock(),war.getBlock(),wild.getBlock())),1,ExplosionResult.DESTROY);
        guard.explode(blast);check(blast.blockList().equals(List.of(wild.getBlock())),"Explosion protected terrain filtered");
        var burn=new BlockBurnEvent(safe.getBlock(),wild.getBlock());guard.burn(burn);check(burn.isCancelled(),"Fire denied");
        var flow=new BlockFromToEvent(new Location(mando,16,70,-200).getBlock(),new Location(mando,15,70,-200).getBlock());guard.flow(flow);check(flow.isCancelled(),"Liquids cannot cross into safezone");
        var piston=new BlockPistonExtendEvent(new Location(mando,17,70,-200).getBlock(),List.of(new Location(mando,16,70,-200).getBlock()),BlockFace.WEST);guard.pistonExtend(piston);check(piston.isCancelled(),"Piston cannot push across border");
        var emptyPiston=new BlockPistonExtendEvent(new Location(mando,16,70,-200).getBlock(),List.of(),BlockFace.WEST);
        guard.pistonExtend(emptyPiston);check(emptyPiston.isCancelled(),"Empty piston head cannot enter safezone");
        var dispenser=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class[]{Block.class},(p,m,args)->{
            if(m.getName().equals("getLocation"))return new Location(mando,16,70,-200);
            if(m.getName().equals("getBlockData"))return Bukkit.createBlockData("minecraft:dispenser[facing=west,triggered=false]");
            if(m.getName().equals("getRelative"))return new Location(mando,15,70,-200).getBlock();return defaultValue(m.getReturnType());
        });
        var dispensing=new BlockDispenseEvent(dispenser,new ItemStack(Material.WATER_BUCKET),new org.bukkit.util.Vector());
        guard.dispense(dispensing);check(dispensing.isCancelled(),"Dispenser bucket cannot write across border");
        var tree=new org.bukkit.event.world.StructureGrowEvent(wild,TreeType.TREE,false,null,new ArrayList<>(List.of(safe.getBlock().getState(),wild.getBlock().getState())));
        guard.tree(tree);check(tree.getBlocks().size()==1&&tree.getBlocks().get(0).getLocation().equals(wild.getBlock().getLocation()),"Tree growth cannot overwrite protected territory");
        var armor=(ArmorStand)Proxy.newProxyInstance(ArmorStand.class.getClassLoader(),new Class[]{ArmorStand.class},(p,m,args)->m.getName().equals("getLocation")?war:defaultValue(m.getReturnType()));
        attacker.location=wild;
        damage=new EntityDamageEvent(armor,EntityDamageEvent.DamageCause.ENTITY_ATTACK,damageSource,5);guard.damage(damage);check(damage.isCancelled(),"Decorative warzone armor stand protected");
        check(safe.getBlock().getBlockData().getAsString().equals(before),"Probe did not edit terrain");
        f.location=safe;service.announce(f.player);check(f.titles.size()==1&&plain(f.titles.get(0).title()).equals("Safezone"),"Safezone title");
        check(f.titles.get(0).title().color().value()==0xFFAA00,"Safezone orange");
        service.announce(f.player);check(f.titles.size()==1,"No duplicate title within same territory");
        f.location=war;service.announce(f.player);check(f.titles.size()==2&&plain(f.titles.get(1).title()).equals("Warzone"),"Warzone title");check(f.titles.get(1).title().color().value()==0xAA0000,"Warzone dark red");
        f.location=wild;service.announce(f.player);check(f.titles.size()==3&&plain(f.titles.get(2).title()).equals("Wilderness"),"Wilderness title");
        var command=Bukkit.getPluginCommand("c");check(command!=null&&command.getPlugin()==owner,"Native command retained");
        for(World world:List.of(mando,batuu)){
            f.location=world==mando?safe:new Location(batuu,10000,93,-10000);f.messages.clear();int loaded=world.getLoadedChunks().length;
            command.execute(f.player,"c",new String[]{"map"});check(loaded==world.getLoadedChunks().length,"Command loads no chunks");
            check(f.messages.size()==(world==mando?36:37),"Expected grid size");int safeCount=0,warCount=0,markers=0;
            for(int z=0;z<32;z++)for(var cell:f.messages.get(z+2).children()){
                check(cell.hoverEvent()!=null,"Chunk hover present");String hover=plain((Component)cell.hoverEvent().value());
                if(plain(cell).equals("+"))markers++;
                if(hover.startsWith("Safezone")){safeCount++;if(!plain(cell).equals("+"))check(cell.color().value()==0xFFAA00,"Safezone cell orange");}
                if(hover.startsWith("Warzone")){warCount++;check(cell.color().value()==0xAA0000,"Warzone cell dark red");}
            }
            check(markers==1,"One player marker");check(safeCount==(world==mando?16:1024),"Correct safe chunks");check(warCount==(world==mando?127:0),"Correct war chunks");
        }
        var vendor=Bukkit.getPluginManager().getPlugin("WeaponMechanics");var type=Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent",true,vendor.getClass().getClassLoader());
        var ctor=type.getConstructors()[0];
        for(Location location:List.of(safe,war,wild)){
            f.location=location;var event=(Event)ctor.newInstance("E-11",new ItemStack(Material.STICK),f.player,EquipmentSlot.HAND);
            for(var listener:event.getHandlers().getRegisteredListeners())if(listener.getListener().getClass().getSimpleName().equals("TerritoryWeapons"))listener.callEvent(event);
            check(((Cancellable)event).isCancelled()==location.equals(safe),"Actual weapon pre-shoot hook "+location);
        }
        f.location=war;service.quit(new PlayerQuitEvent(f.player,Component.empty()));service.announce(f.player);check(f.titles.size()==4,"Quit clears title state");
        Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("passed.txt"),"NATIVE TERRITORY PASSED: "+checks+" assertions\n");
        getLogger().info("NATIVE TERRITORY PASSED: "+checks+" assertions");
    }
}
