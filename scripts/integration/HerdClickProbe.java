package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import net.mandomc.gameplay.mobs.HerdSettings;
import net.mandomc.integrations.mobs.*;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated real client click paths and outgoing horse-screen evidence, no direct interaction calls. */
public final class HerdClickProbe extends JavaPlugin {
 NativeProbePlayers actors; Player owner; Horse horse; HerdAnimals animals; MythicMobAccess mobs; HerdBabies babies;
 int index,checks; final List<String> evidence=new ArrayList<>();
 public void onEnable(){if(Bukkit.getPort()!=25576||!Bukkit.getWorlds().getFirst().getName().equals("herd_test"))throw new IllegalStateException("Isolated herd runtime only");later(120,this::start);}
 interface Step{void run()throws Exception;}
 void later(long t,Step action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"HERD CLICK FAILED",e);save("CLICKS-FAILED.txt",e.toString());if(actors!=null){actors.close();actors=null;}Bukkit.shutdown();}},t);}
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;evidence.add("PASS "+label);}
 void save(String name,String message){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name),message+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
 void start()throws Exception{
  var w=Bukkit.getWorlds().getFirst();for(int x=90;x<115;x++)for(int z=90;z<115;z++){w.getBlockAt(x,19,z).setType(Material.SANDSTONE);for(int y=20;y<29;y++)w.getBlockAt(x,y,z).setType(Material.AIR);}
  w.getChunkAt(6,6).addPluginChunkTicket(this);
  mobs=new MythicMobAccess();for(var e:w.getLivingEntities())if(mobs.managed(e))mobs.remove(e);
  babies=new HerdBabies(this,mobs);var models=new HerdModels(this);var cfg=new YamlConfiguration();cfg.set("schema-version",1);cfg.set("tame-chance",1);cfg.set("bantha-food","HAY_BLOCK");cfg.set("dewback-food","CACTUS");
  animals=new HerdAnimals(this,mobs,babies,models,HerdSettings.read(cfg),new YamlConfiguration(),l->true);new HerdTempt(this,mobs,Material.HAY_BLOCK,Material.CACTUS,animals);
  actors=new NativeProbePlayers("HerdClick");owner=actors.create(new Location(w,100.5,20,103.5));horse=(Horse)mobs.spawn("Bantha",new Location(w,100.5,20,100.5),1);horse.setAI(false);mobs.persist(horse);
  later(20,()->{owner.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK));evidence.add("beforefeed player="+owner.getLocation()+" valid="+owner.isValid()+" horse="+horse.getLocation()+" valid="+horse.isValid()+" type="+mobs.type(horse)+" occupied="+HerdModels.ridden(horse)+" owner="+horse.getOwnerUniqueId()+" tamed="+horse.isTamed());check(animals.feed(owner,horse,EquipmentSlot.HAND),"tame with normal feeding service");owner.getInventory().clear();later(10,this::click);});
 }
 void click()throws Exception{
  owner.closeInventory();actors.drain(owner);actors.otherPackets(owner);
  var model=ModelEngineAPI.getModeledEntity(horse).getModels().values().iterator().next();var hitbox=((DisplayRenderer)model.getModelRenderer()).getHitbox();
  var parser=Class.forName("com.ticxo.modelengine.v26_1.parser.model.DisplayParser",true,ModelEngineAPI.class.getClassLoader());var spawnMethod=parser.getDeclaredMethod("hitboxSpawn",DisplayRenderer.Hitbox.class);spawnMethod.setAccessible(true);var spawn=spawnMethod.invoke(parser.getConstructor().newInstance(),hitbox);
  evidence.add("route="+index+" main="+model.isMainHitbox()+" visible="+hitbox.isHitboxVisible()+" spawn="+spawn+" horse="+horse.getLocation()+" owner="+owner.getLocation());
  int id=index%2==0?hitbox.getHitboxId():horse.getEntityId();var tracker=ModelEngineAPI.getInteractionTracker();Integer relay=tracker.getEntityRelay(id);if(relay==null&&tracker.getModelRelay(id)!=null)relay=horse.getEntityId();
  if(index%2==0)check(spawn!=null,"actual renderer hitbox is sent to the client");
  check(ModelEngineAPI.shouldShow(owner,horse.getEntityId())!=com.ticxo.modelengine.api.nms.network.CancelType.HIDE,"client retains actual horse required by native mount screen");
  outboundHorseSpawn();
  packet(id,relay);
  later(10,()->{actors.drain(owner);var packets=actors.otherPackets(owner);evidence.add("packets="+packets+" holder="+owner.getOpenInventory().getTopInventory().getHolder());check(owner.getOpenInventory().getTopInventory().getHolder()==horse,"client click route "+index+" keeps native saddle menu open");check(packets!=null&&packets.stream().anyMatch(p->p.contains("MountScreenOpen")),"native horse screen packet emitted route "+index);
   if(++index==4){dewback();return;}
   if(index==8){save("CLICKS-PASSED.txt","HERD CLICK PASSED "+checks);actors.close();actors=null;Bukkit.shutdown();return;}
   if(index%4==2){owner.teleport(horse.getLocation().clone().add(0,0,4.5));}
   later(3,this::click);
  });
 }
 void dewback()throws Exception{
  mobs.remove(horse);var at=new Location(owner.getWorld(),100.5,20,100.5);horse=(Horse)mobs.spawn("Dewback",at,1);horse.setAI(false);mobs.persist(horse);owner.teleport(at.clone().add(0,0,3));
  var baby=(Horse)babies.spawn("Dewback",at.clone().add(8,0,0),1);baby.setAI(false);
  later(20,()->{
   evidence.add("Dewback blueprint="+ModelEngineAPI.getBlueprint("dewback").getMainHitbox().toSimpleString()+" native="+horse.getBoundingBox());
   check(Math.abs(horse.getWidth()-3.5)<.001&&Math.abs(horse.getHeight()-2.25)<.001,"Dewback body collision is 3.5 wide and 2.25 tall");
   check(Math.abs(baby.getWidth()/horse.getWidth()-.5)<.001&&Math.abs(baby.getHeight()/horse.getHeight()-.5)<.001,"baby Dewback collision stays uniformly half-size");
   owner.getInventory().setItemInMainHand(new ItemStack(Material.CACTUS));check(animals.feed(owner,horse,EquipmentSlot.HAND),"Dewback tame succeeds");owner.getInventory().clear();later(10,this::click);
  });
 }
 void outboundHorseSpawn()throws Exception{
  var handle=horse.getClass().getMethod("getHandle").invoke(horse);var packetType=Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");var entityType=Class.forName("net.minecraft.world.entity.Entity");var block=Class.forName("net.minecraft.core.BlockPos");
  var packet=packetType.getConstructor(entityType,int.class,block).newInstance(handle,0,block.getField("ZERO").get(null));
  var wrapper=new com.ticxo.modelengine.api.nms.network.PipelineWrapper(owner,new io.netty.channel.embedded.EmbeddedChannel());
  var type=Class.forName("com.ticxo.modelengine.v26_1.network.ModelEngineChannelHandler",true,ModelEngineAPI.class.getClassLoader());var filter=type.getConstructor(Player.class,com.ticxo.modelengine.api.nms.network.PipelineWrapper.class).newInstance(owner,wrapper);
  var method=type.getDeclaredMethod("handleAddEntity",packetType);method.setAccessible(true);var sent=method.invoke(filter,packet);
  check(sent!=null,"actual ModelEngine outgoing filter sends native horse spawn");
  check(packetType.getMethod("getId").invoke(sent).equals(horse.getEntityId()),"client horse spawn preserves real inventory-owner ID");
  check(ModelEngineAPI.shouldShow(owner,horse.getEntityId())==com.ticxo.modelengine.api.nms.network.CancelType.INVIS&&horse.isInvisible()&&ModelEngineAPI.getEntityHandler().isForcedInvisible(horse),"native horse stays invisible while client can resolve it");
  var dataValue=Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");var serializer=Class.forName("net.minecraft.network.syncher.EntityDataSerializer");var serials=Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
  var flags=dataValue.getConstructor(int.class,serializer,Object.class).newInstance(0,serials.getField("BYTE").get(null),(byte)0);
  var metaType=Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");var metadata=metaType.getConstructor(int.class,List.class).newInstance(horse.getEntityId(),List.of(flags));
  var metaFilter=type.getDeclaredMethod("handleEntityData",metaType);metaFilter.setAccessible(true);var hidden=metaFilter.invoke(filter,metadata);
  var packed=(List<?>)metaType.getMethod("packedItems").invoke(hidden);byte value=(Byte)dataValue.getMethod("value").invoke(packed.getFirst());
  check((value&0x20)!=0,"actual outgoing metadata forces native horse body invisible");

  // Kept open for the wrapper's asynchronous native writes until test shutdown.
 }
 void packet(int id,Integer relay)throws Exception{
  var handle=owner.getClass().getMethod("getHandle").invoke(owner);var listener=handle.getClass().getField("connection").get(handle);var hand=Class.forName("net.minecraft.world.InteractionHand");var vec=Class.forName("net.minecraft.world.phys.Vec3");var type=Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
  var packet=type.getConstructor(int.class,hand,vec,boolean.class).newInstance(id,hand.getField("MAIN_HAND").get(null),vec.getConstructor(double.class,double.class,double.class).newInstance(0,1,0),false);
  if(relay==null){listener.getClass().getMethod("handleInteract",type).invoke(listener,packet);return;}
  var wrapper=Class.forName("com.ticxo.modelengine.v26_1.network.patch.ServerboundInteractPacketWrapper",true,ModelEngineAPI.class.getClassLoader());var wrapped=wrapper.getConstructor(int.class,type).newInstance(relay,packet);wrapper.getMethod("handle",Class.forName("net.minecraft.network.protocol.game.ServerGamePacketListener")).invoke(wrapped,listener);
 }
}
