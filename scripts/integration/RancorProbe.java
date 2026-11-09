package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import com.ticxo.modelengine.api.ModelEngineAPI;
import net.mandomc.gameplay.mobs.RancorSlam;
/** Real command/model/slam checks on a restored, isolated two-material sky platform. */
public final class RancorProbe extends JavaPlugin {
 private World world;private Mob boss;private Cow enemy;private int checks;private final List<BlockState> blocks=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();private final List<Mob> extra=new ArrayList<>();
 @Override public void onEnable(){later(120,this::begin);}
 private void begin() throws Exception {
  world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));for(int x=72;x<=75;x++)for(int z=72;z<=75;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
  for(int x=1164;x<=1196;x++)for(int z=1164;z<=1196;z++){var b=world.getBlockAt(x,300,z);if(!b.getType().isAir())throw new IllegalStateException("Sky fixture occupied");blocks.add(b.getState());b.setType(x<1180?Material.POLISHED_DEEPSLATE:Material.RED_SANDSTONE,false);}
  check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mm mobs spawn Rancor 1 "+world.getName()+",1180.5,301,1180.5"),"real Mythic spawn command accepted");
  boss=world.getLivingEntities().stream().filter(e->e instanceof Mob && e.getLocation().distanceSquared(new Location(world,1180.5,301,1180.5))<2 && MythicBukkit.inst().getAPIHelper().isMythicMob(e)).map(e->(Mob)e).findFirst().orElseThrow(()->new AssertionError("Real spawn command did not create Rancor"));
  boss.setAI(false);boss.setGravity(false);boss.setRemoveWhenFarAway(false);
  enemy=world.spawn(boss.getLocation().add(0,0,6),Cow.class,e->{e.setAI(false);e.setGravity(false);});select();suppress();
  Bukkit.getScheduler().runTaskTimer(this,()->{if(boss.isValid())select();},1,1);
  later(10,()->{var model=model();check(boss.getType()==EntityType.PIG,"legacy pig base retained");check(boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()==750,"legacy 750 health retained");check(Math.abs(model.getScale().x()-2)<0.01,"legacy model scale two retained");check(boss.getBoundingBox().getWidthX()>2,"original large model hitbox attached");
   for(var a:ModelEngineAPI.getBlueprint("rancor").getAnimations().values())if(a.getName().startsWith("attack"))check(a.getLoopMode().name().equals("ONCE"),"attack finishes: "+a.getName());
   attack(0);
  });
 }
 private com.ticxo.modelengine.api.model.ActiveModel model(){return ModelEngineAPI.getModeledEntity(boss).getModels().get("rancor");}
 private void attack(int i){var names=List.of("swing_left","swing_right","stomp_left","stomp_right");if(i==names.size()){slam();return;}var name=names.get(i);boss.setRotation(180,0);ModelEngineAPI.getModeledEntity(boss).setYBodyRotImmediately(180);cast("rancor_"+name);
  later(5,()->check(!model().getAnimationHandler().isPlayingAnimation("attack."+name),name+" faces before animation"));
  later(8,()->{check(model().getAnimationHandler().isPlayingAnimation("attack."+name),name+" original animation plays");check(Math.abs(Math.IEEEremainder(ModelEngineAPI.getModeledEntity(boss).getYBodyRot(),360))<10,name+" faces current target");if(name.startsWith("swing"))check(new net.mandomc.integrations.models.RancorBone().position(boss,name.endsWith("left")?"left_hand":"right_hand").filter(p->p.distanceSquared(boss.getLocation())>1).isPresent(),"crescent follows an actual animated hand");});later(22,()->{if(name.startsWith("swing"))swipeKnockback();});later(26,()->{if(name.startsWith("stomp")){check(debris().stream().anyMatch(d->d.getScoreboardTags().contains("mandomc_rancor_stomp")),name+" makes a distinct foot-focused eruption");check(debris().stream().allMatch(d->d.getTransformation().getScale().x()>=0.5),"stomp uses heavier debris chunks");}});later(40,()->attack(i+1));
 }
 private void slam(){cast("rancor_slam");later(19,()->check(debris().isEmpty(),"slam rubble waits for the animation impact cue"));
  later(25,()->{check(!debris().isEmpty(),"slam impact launches real visible rubble");check(maxForward()<=4.1,"rubble begins in the near forward strip");});
  later(30,()->check(maxForward()>4.1&&maxForward()<=10.1,"ground rupture advances toward target"));
  later(36,()->{check(maxForward()>13.5&&maxForward()<=14.1,"rupture reaches fourteen blocks toward target");check(debris().size()<=256,"fragment count stays within global cap");check(debris().stream().allMatch(d->Math.abs(d.getX()-1180.5)<=1.6&&d.getZ()>1180.5),"slam is a forward line, with no rear or radial debris");
   var materials=new HashSet<Material>();for(var d:debris()){materials.add(d.getBlock().getMaterial());if(d.getBlock().getMaterial()!=world.getBlockAt(d.getLocation().getBlockX(),300,d.getLocation().getBlockZ()).getType())throw new AssertionError("Rubble did not match floor");if(d.isPersistent()||d.hasGravity())throw new AssertionError("Physical/persistent rubble");}
   check(materials.equals(Set.of(Material.POLISHED_DEEPSLATE,Material.RED_SANDSTONE)),"debris uses both actual floor materials");
   check(debris().stream().anyMatch(d->d.getY()>301.2),"rubble visibly lifts off the floor");check(world.getEntitiesByClass(FallingBlock.class).isEmpty(),"no physical falling blocks created");verifyFloor();
  });
  later(65,()->{check(debris().isEmpty(),"all slam fragments expire");cast("rancor_slam_shockwave");later(4,()->{check(!debris().isEmpty(),"next wave starts cleanly");boss.remove();later(3,()->{check(debris().isEmpty(),"removing Rancor removes active rubble");budget();});});});
 }
 private void budget() throws Exception {
  for(int i=0;i<5;i++){var actor=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob("Rancor",new Location(world,1180.5,301,1180.5),1);actor.setAI(false);actor.setGravity(false);extra.add(actor);Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc rancorslam "+actor.getUniqueId());}
  later(16,()->{check(!debris().isEmpty()&&debris().size()<=256,"multiple simultaneous Rancors obey global fragment cap");extra.forEach(Entity::remove);later(3,()->{check(debris().isEmpty(),"all owner cleanup removes concurrent debris");verifyFloor();getLogger().info("RANCOR PROBE PASSED: "+checks+" assertions");cleanup();});});
 }
 private void swipeKnockback() throws Exception {
  var plugin=(net.mandomc.bootstrap.MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");var field=plugin.getClass().getDeclaredField("rancorSlam");field.setAccessible(true);var effect=(RancorSlam)field.get(plugin);var map=effect.getClass().getDeclaredField("trails");map.setAccessible(true);var trail=((Map<?,?>)map.get(effect)).get(boss.getUniqueId());check(trail!=null,"swipe trail active at contact");var motion=trail.getClass().getDeclaredField("motion");motion.setAccessible(true);var direction=((org.bukkit.util.Vector)motion.get(trail)).clone();check(direction.lengthSquared()>0.001,"swipe has animated hand-motion direction");direction.normalize();
  var actor=nativeTestPlayer(world,boss.getLocation().add(0,0,4));Player proxy=(Player)java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,args)->{if(m.getName().equals("isValid")||m.getName().equals("isOnline"))return true;return m.invoke(actor,args);});
  var event=new org.bukkit.event.entity.EntityDamageByEntityEvent(boss,proxy,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,8);effect.hit(event);later(2,()->{var v=actor.getVelocity();check(v.getY()>0.3&&v.clone().setY(0).normalize().dot(direction)>0.9,"connected swipe launches player along the actual arm path");actor.setVelocity(new org.bukkit.util.Vector());var denied=new org.bukkit.event.entity.EntityDamageByEntityEvent(boss,proxy,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,8);denied.setCancelled(true);effect.hit(denied);later(2,()->check(actor.getVelocity().lengthSquared()==0,"cancelled swipe cannot launch player"));});
 }
 private void verifyFloor(){for(var b:blocks){var expected=b.getX()<1180?Material.POLISHED_DEEPSLATE:Material.RED_SANDSTONE;if(b.getBlock().getType()!=expected)throw new AssertionError("Slam changed real terrain");}check(true,"real terrain remains unchanged");}
 private List<BlockDisplay> debris(){return world.getEntitiesByClass(BlockDisplay.class).stream().filter(d->d.getScoreboardTags().contains(RancorSlam.TAG)).toList();}
 private double maxForward(){return debris().stream().mapToDouble(d->d.getZ()-1180.5).max().orElse(0);}
 private double maxRadius(){return debris().stream().mapToDouble(d->Math.hypot(d.getX()-1180.5,d.getZ()-1180.5)).max().orElse(0);}
 private void select(){var api=MythicBukkit.inst().getAPIHelper();boss.setTarget(enemy);api.addThreat(boss,enemy,100);api.getMythicMobInstance(boss).setTarget(BukkitAdapter.adapt(enemy));}
 private void suppress(){MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss).setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill("rancor_attack_selector").orElseThrow(),600000);}
 private void cast(String s){check(MythicBukkit.inst().getAPIHelper().castSkill(boss,s,boss.getLocation(),List.of(enemy),List.of(),1),"cast "+s);}
 private interface Check{void run() throws Exception;}
 private void later(long d,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"RANCOR PROBE FAILED",e);cleanup();}},d);}
 private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;getLogger().info("PASS: "+s);}
 private void cleanup(){Bukkit.getScheduler().cancelTasks(this);if(boss!=null)boss.remove();if(enemy!=null)enemy.remove();extra.forEach(Entity::remove);extra.clear();blocks.forEach(b->b.update(true,false));blocks.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
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
