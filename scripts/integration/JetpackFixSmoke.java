package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.boba.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Narrow exact-candidate startup gate, on a disposable copy of the current runtime only. */
public final class JetpackFixSmoke extends JavaPlugin {
    private MandoPlugin main;
    private BobaEquipment equipment;
    private Player actor;
    private Cow target;
    private int checks;
    @Override public void onEnable() {later(160,this::begin);}
    private void begin() throws Exception {
        main=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(main!=null&&main.isEnabled(),"exact candidate plugin enabled");
        var failed=MandoPlugin.class.getDeclaredField("failed");failed.setAccessible(true);
        check(!failed.getBoolean(main),"full MandoMC bootstrap finished without fail-closed login gate");
        check(main.worlds()!=null&&main.warps()!=null&&main.events()!=null,"complete world/arrival/travel/event startup finished");
        var feature=Bukkit.getServicesManager().load(BobaEquipmentFeature.class);
        check(feature!=null,"additive bootstrap registered BobaEquipmentFeature");equipment=feature.equipment();
        for(String id:List.of("boba_jetpack","boba_flamethrower","ee3","standard_plasma_cells","jawa_ionization_blaster"))
            check(main.items().create(id)!=null,"actual current catalog creates "+id);
        check(main.getCommand("mandomc").getTabCompleter().onTabComplete(Bukkit.getConsoleSender(),main.getCommand("mandomc"),"mmc",new String[]{"eq"}).contains("equipment"),"actual equipment route registered");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(main.getDataFolder(),"mobs/drops/jabba.yml"));
        check(config.getDouble("drops.BobaFett.equipment.boba_jetpack")==.10&&config.getDouble("drops.BobaFett.equipment.boba_flamethrower")==.15,"actual runtime loot installer registered both independent rolls");
        check(config.getStringList("drops.JawaBrown.salvage.pool").size()>0,"runtime Jawa salvage retained");
        var protection=new net.mandomc.integrations.regions.SaberProtection();Location where=null;
        var world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));
        for(int x=-224;x<=224&&where==null;x+=16)for(int z=-224;z<=224;z+=16){
            var test=new Location(world,x+.5,180,z+.5,0,0);
            if(protection.allowed(test)&&protection.allowed(test.clone().add(0,0,4))){where=test;break;}
        }
        check(where!=null,"current native territory policy supplies an allowed isolated weapon fixture");
        where.getChunk().addPluginChunkTicket(this);actor=nativePlayer(where);
        actor.setGameMode(GameMode.SURVIVAL);
        actor.getInventory().setItemInOffHand(main.items().create(BobaEquipment.JETPACK));
        actor.getInventory().setItemInMainHand(main.items().create(BobaEquipment.FLAMETHROWER));
        actor.getInventory().setItem(1,new ItemStack(Material.COAL));actor.getInventory().setItem(2,new ItemStack(Material.FIRE_CHARGE,2));
        var armor=new ItemStack(Material.NETHERITE_CHESTPLATE);var meta=armor.getItemMeta();
        ((org.bukkit.inventory.meta.Damageable)meta).setDamage(37);meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,4,true);armor.setItemMeta(meta);
        actor.getInventory().setChestplate(armor);
        check(equipment.toggle(actor),"actual registered jetpack activates");
        check(actor.getInventory().getItem(1)==null,"actual registered jetpack consumes first coal");
        check(actor.getInventory().getChestplate().equals(armor),"actual registered jetpack preserves chest metadata");
        check(equipment.attachment(actor)!=null&&equipment.attachment(actor).isValid(),"actual registered jetpack spawns attached display");
        check(actor.isGliding(),"registered native offhand equipment enables glide state");
        check(Math.abs(equipment.seconds(actor)-.1)<.0001,"one newly consumed coal funds exactly 0.1 seconds");
        check(equipment.ownsActionBar(actor),"fuel warning/actionbar ownership is exposed to reload UI");
        equipment.toggle(actor);attachmentProof(where);check(equipment.toggle(actor),"resuming attachment after movement retains purchased fuel fraction");
        later(4,()->check(!equipment.powered(actor),"0.1-second real-time interval depletes without another hotbar coal"));
        target=world.spawn(where.clone().add(0,0,3),Cow.class,c->{c.setAI(false);c.setGravity(false);});
        check(equipment.flames().activate(actor),"actual registered flame service accepts burst in native policy");
        check(actor.getInventory().getItem(2).getAmount()==1,"registered burst consumes exactly one fire charge");
        check(!equipment.flames().activate(actor)&&actor.getInventory().getItem(2).getAmount()==1,"registered cooldown prevents second ammo/use");
        later(45,()->{
            check(!equipment.powered(actor),"registered jetpack depletes after its purchased 0.1-second interval");
            check(target.getHealth()<target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"registered native flame packets damage a real target");
            check(actor.getInventory().getItem(2).getAmount()==1,"entire registered burst spent only one fire charge");
            equipment.detach(actor);
            check(equipment.attachment(actor)==null,"registered cleanup removes attachment");
            getLogger().info("JETPACK FIX SMOKE PASSED: "+checks+" assertions; exact candidate/full startup/native registered services");
            cleanup();Bukkit.shutdown();
        });
    }
    private void attachmentProof(Location original)throws Exception {
        var display=equipment.attachment(actor);
        check(display.getVehicle()==actor&&actor.getPassengers().contains(display),"real native passenger is attached to wearer");
        check(!actor.isInsideVehicle(),"wearing attachment does not turn player into vehicle passenger");
        var actorHandle=actor.getClass().getMethod("getHandle").invoke(actor);
        var displayHandle=display.getClass().getMethod("getHandle").invoke(display);
        var nativeEntity=Class.forName("net.minecraft.world.entity.Entity");
        var riding=nativeEntity.getMethod("positionRider",nativeEntity);
        var move=nativeEntity.getMethod("setPos",double.class,double.class,double.class);
        var tick=BobaEquipment.class.getDeclaredMethod("tick");tick.setAccessible(true);
        actor.setGliding(false);actor.setSneaking(false);
        for(float yaw:new float[]{0,90,180,270}) {
            actor.setRotation(yaw+35,50);actor.setBodyYaw(yaw);
            tick.invoke(equipment);riding.invoke(actorHandle,displayHandle);
            var expected=JetpackPose.at(actor.getLocation(),yaw,50,false,false);
            var content=display.getLocation().add(display.getTransformation().getTranslation().x,
                    display.getTransformation().getTranslation().y,display.getTransformation().getTranslation().z);
            getLogger().info("MOUNT yaw="+yaw+" actualBody="+actor.getBodyYaw()+" glide="+actor.isGliding()+" powered="+equipment.powered(actor)+" mounted="+(display.getVehicle()==actor)+" content="+content+" expected="+expected.center()+" translation="+display.getTransformation().getTranslation());
            check(content.distance(expected.center())<.00001,"native mount pivot places torso origin exactly at yaw "+yaw);
            var effective=new org.joml.Quaternionf(display.getTransformation().getLeftRotation()).rotateY((float)Math.PI);
            var back=new org.joml.Vector3f(0,0,1).rotate(effective);
            var forward=new org.joml.Vector3f(-(float)Math.sin(Math.toRadians(yaw)),0,(float)Math.cos(Math.toRadians(yaw)));
            check(back.dot(forward)<-.999,"actual client half-turn leaves geometry behind body at yaw "+yaw);
            // The isolated actor is not a chunk-loader; a connected player keeps the destination loaded.
            actor.getWorld().getChunkAt((int)Math.floor(actor.getX()+18)>>4,(int)Math.floor(actor.getZ()+7)>>4).addPluginChunkTicket(this);
            move.invoke(actorHandle,actor.getX()+18,actor.getY()+2,actor.getZ()+7);riding.invoke(actorHandle,displayHandle);tick.invoke(equipment);
            check(display.isValid()&&display.getVehicle()==actor,"native attachment survives movement into loaded player destination chunk");
            var moved=display.getLocation().add(display.getTransformation().getTranslation().x,
                    display.getTransformation().getTranslation().y,display.getTransformation().getTranslation().z);
            check(moved.distance(JetpackPose.at(actor.getLocation(),yaw,50,false,false).center())<.00001,
                    "native mount follows 18-block parent movement without absolute teleport lag at yaw "+yaw);
        }
        actor.setSneaking(true);tick.invoke(equipment);riding.invoke(actorHandle,displayHandle);
        var actual=display.getLocation().add(display.getTransformation().getTranslation().x,
                display.getTransformation().getTranslation().y,display.getTransformation().getTranslation().z);
        check(actual.distance(JetpackPose.at(actor.getLocation(),actor.getBodyYaw(),actor.getPitch(),true,false).center())<.00001,"sneaking mount translation uses exact native pivot");
        actor.setSneaking(false);actor.setGliding(true);actor.setRotation(0,0);actor.setBodyYaw(0);tick.invoke(equipment);
        var effective=new org.joml.Quaternionf(display.getTransformation().getLeftRotation()).rotateY((float)Math.PI);
        check(new org.joml.Vector3f(0,0,1).rotate(effective).y>.999,"gliding geometry is above horizontal back rather than below/front");
        check(display.getTeleportDuration()==0&&display.getVehicle()==actor,"display follows via passenger sync with zero teleport interpolation");
        move.invoke(actorHandle,original.getX(),original.getY(),original.getZ());riding.invoke(actorHandle,displayHandle);tick.invoke(equipment);
    }
    private Player nativePlayer(Location location)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());Object level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaStartupTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try{var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;}catch(NoSuchFieldException ignored){}
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
    private interface Work {void run()throws Exception;}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JETPACK FIX SMOKE FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
    private void cleanup(){if(equipment!=null&&actor!=null)equipment.detach(actor);if(main!=null&&main.combat()!=null&&actor!=null)main.combat().clear(actor);if(target!=null)target.remove();}
    @Override public void onDisable(){cleanup();}
}
