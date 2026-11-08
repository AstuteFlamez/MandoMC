package net.mandomc.verification;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.ItemPresentationRefresh;
import io.papermc.paper.datacomponent.DataComponentTypes;

/** Native hunger restoration and Mythic death-list validation; never touches real player data. */
public final class FoodProbe extends JavaPlugin {
    private final List<Entity> entities=new ArrayList<>();
    private final Listener observer=new Listener(){};
    private int checks,legsSpawned;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{verify();}catch(Throwable e){fail(e);}},120);}
    private void verify() throws Exception {
        var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        var world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        var point=new Location(world,600.5,301,600.5);
        var player=nativeTestPlayer(world,point);
        for(String id:List.of("dewback_leg","massiff_ribs")){
            var item=plugin.items().create(id);int food=id.equals("dewback_leg")?8:4;
            check(item.getData(DataComponentTypes.FOOD).nutrition()==food,id+" native nutrition is "+food);
            check(item.getItemMeta().getItemModel().toString().equals("mandomc:consumables/"+id),id+" uses shipped texture");
            item.setAmount(3);player.getInventory().setItemInMainHand(item);player.setFoodLevel(2);player.setSaturation(0);
            player.startUsingItem(EquipmentSlot.HAND);
            check(player.hasActiveItem(),id+" starts native consumption");
            player.completeUsingActiveItem();
            check(player.getFoodLevel()==2+food,id+" actually restores "+food+" hunger points");
            check(player.getInventory().getItemInMainHand().getAmount()==2,id+" consumes exactly one item");
        }
        var old=plugin.items().create("massiff_ribs");old.setAmount(7);
        old.setData(DataComponentTypes.FOOD,new ItemStack(Material.COOKED_MUTTON).getData(DataComponentTypes.FOOD));
        check(ItemPresentationRefresh.refresh(plugin.items(),old),"old ribs food metadata refreshes");
        check(old.getAmount()==7 && old.getData(DataComponentTypes.FOOD).nutrition()==4,"old rib count retained and hunger corrected");
        for(String type:List.of("Dewback","Massiff","TuskenRaiderSniper")){
            var entity=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob(type,point);
            entities.add(entity);entity.setAI(false);entity.setGravity(false);
            var mob=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);int count=0;
            for(int i=0;i<400;i++){
                var event=new MythicMobDeathEvent(mob,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(event);
                int foods=0;
                for(var drop:event.getDrops()){
                    var id=plugin.items().identify(drop);
                    if(!Set.of("dewback_leg","massiff_ribs").contains(id==null?"":id))continue;
                    check(id.equals(type.equals("Dewback")?"dewback_leg":"massiff_ribs"),"food belongs to correct species");
                    foods+=drop.getAmount();
                }
                check(foods<=1,"at most one food per death");count+=foods;
            }
            check(type.equals("Dewback")?count==400:type.equals("Massiff")?count>=240&&count<=360:count==0,
                    type+" food drops "+count+"/400");
        }
        var real=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob("Dewback",point);
        entities.add(real);
        Bukkit.getPluginManager().registerEvent(ItemSpawnEvent.class,observer,EventPriority.MONITOR,(l,e)->{
            var item=((ItemSpawnEvent)e).getEntity();
            if(item.getLocation().distanceSquared(point)<16 && "dewback_leg".equals(plugin.items().identify(item.getItemStack()))){
                legsSpawned+=item.getItemStack().getAmount();entities.add(item);
            }
        },this,true);
        real.setHealth(0);
        Bukkit.getScheduler().runTaskLater(this,()->{try{
            check(legsSpawned==1,"one real Dewback death spawns exactly one collectible leg");
            getLogger().info("FOOD PROBE PASSED: "+checks+" assertions");cleanup();
        }catch(Throwable e){fail(e);}},3);
    }
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"FOOD PROBE FAILED",e);cleanup();}
    private void cleanup(){HandlerList.unregisterAll(observer);entities.forEach(Entity::remove);entities.clear();}
    @Override public void onDisable(){cleanup();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "MobTest");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

}
