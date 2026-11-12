package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.mandomc.gameplay.mobs.HerdSettings;
import net.mandomc.integrations.mobs.*;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated native model packets, real GUI container packets, lead physics/fences and restart. */
public final class HerdLeadProbe extends JavaPlugin implements Listener {
    private NativeProbePlayers actors; private Player owner, other; private Horse horse, wild, baby;
    private MythicMobAccess mobs; private HerdAnimals animals; private HerdModels models;
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private boolean cancelLeash, cancelUnleash; private int leashEvents, checks;
    private final List<String> rows = new ArrayList<>(); private Location point; private Path state;
    interface Step { void run() throws Exception; }
    private void later(long ticks, Step action) { Bukkit.getScheduler().runTaskLater(this, () -> {try {action.run();} catch(Throwable e) {fail(e);}}, ticks); }
    private void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); checks++;rows.add("PASS "+label); }
    public void onEnable() {
        if(Bukkit.getPort()!=25576 || !Bukkit.getWorlds().getFirst().getName().equals("herd_test")) throw new IllegalStateException("Isolated runtime only");
        later(120,this::start);
    }
    private void start() throws Exception {
        var w=Bukkit.getWorlds().getFirst();point=new Location(w,100.5,20,100.5);w.getChunkAt(point).addPluginChunkTicket(this);
        mobs=new MythicMobAccess();var babies=new HerdBabies(this,mobs);models=new HerdModels(this);
        var cfg=new YamlConfiguration();cfg.set("schema-version",1);cfg.set("bantha-food","HAY_BLOCK");cfg.set("dewback-food","CACTUS");cfg.set("tame-chance",1);
        animals=new HerdAnimals(this,mobs,babies,models,HerdSettings.read(cfg),new YamlConfiguration(),l->allowed.get());
        new HerdTempt(this,mobs,Material.HAY_BLOCK,Material.CACTUS,animals);Bukkit.getPluginManager().registerEvents(this,this);
        Files.createDirectories(getDataFolder().toPath());state=getDataFolder().toPath().resolve("lead-restart.txt");
        actors=new NativeProbePlayers("LeadTest");owner=actors.create(point.clone().add(0,0,3),Files.exists(state)?UUID.fromString(Files.readAllLines(state).get(1)):UUID.randomUUID());other=actors.create(point.clone().add(2,0,3));
        if(Files.exists(state)){later(20,this::restart);return;}
        for(int x=90;x<115;x++)for(int z=90;z<115;z++){w.getBlockAt(x,19,z).setType(Material.SANDSTONE);for(int y=20;y<28;y++)w.getBlockAt(x,y,z).setType(Material.AIR);}
        for(var e:w.getLivingEntities())if(animals.herd(e))mobs.remove(e);
        horse=(Horse)mobs.spawn("Bantha",point,1);horse.setAI(false);mobs.persist(horse);
        wild=(Horse)mobs.spawn("Dewback",point.clone().add(3,0,0),1);wild.setAI(false);mobs.persist(wild);
        baby=(Horse)babies.spawn("Dewback",point.clone().add(-3,0,0),1);baby.setOwner(owner);baby.setAI(false);
        later(20,()->{owner.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK));check(animals.feed(owner,horse,EquipmentSlot.HAND),"tame existing GUI fixture normally");owner.getInventory().clear();later(10,this::gui);});
    }
    private void gui() throws Exception {
        modelClick(owner,horse);later(3,()->{
            check(HerdEquipment.isMenu(owner.getOpenInventory().getTopInventory(),horse),"actual renderer click opens custom GUI");
            check(owner.getOpenInventory().getTopInventory().getSize()==9,"compact equipment row");
            var pane=owner.getOpenInventory().getTopInventory().getItem(0);check(pane.getType()==Material.GRAY_STAINED_GLASS_PANE&&pane.getItemMeta().isHideTooltip(),"shared untakeable GUI background");
            actors.drain(owner);var packets=actors.otherPackets(owner);check(packets!=null&&packets.stream().anyMatch(p->p.endsWith("ClientboundOpenScreenPacket"))&&packets.stream().noneMatch(p->p.contains("MountScreenOpen")),"custom screen packet replaces horse menu");
            owner.setItemOnCursor(new ItemStack(Material.SADDLE,2));containerClick(owner,4,"PICKUP");
            check(HerdModels.saddled(horse)&&owner.getItemOnCursor().getAmount()==1,"actual client container click transfers one saddle to native authority");
            containerClick(owner,0,"PICKUP");check(owner.getItemOnCursor().getAmount()==1&&HerdModels.saddled(horse),"filler click cannot take or duplicate display item");
            owner.setItemOnCursor(new ItemStack(Material.AIR));containerClick(owner,4,"PICKUP");
            check(!HerdModels.saddled(horse)&&owner.getItemOnCursor().getAmount()==1,"actual client removal returns one original saddle");
            owner.setItemOnCursor(new ItemStack(Material.AIR));owner.getInventory().setItem(0,new ItemStack(Material.SADDLE,2));containerClick(owner,36,"QUICK_MOVE");
            check(HerdModels.saddled(horse)&&owner.getInventory().getItem(0).getAmount()==1,"actual shift packet consumes exactly one storage saddle");
            allowed.set(false);owner.setItemOnCursor(new ItemStack(Material.AIR));containerClick(owner,4,"PICKUP");check(HerdModels.saddled(horse)&&owner.getItemOnCursor().isEmpty(),"protected GUI mutation consumes and returns nothing");allowed.set(true);
            owner.closeInventory();check(HerdModels.saddled(horse),"closing GUI preserves single native saddle");
            owner.getInventory().clear();owner.getInventory().setItemInMainHand(new ItemStack(Material.LEAD,3));cancelLeash=true;modelClick(owner,horse);later(3,this::cancelledAttach);
        });
    }
    private void cancelledAttach() throws Exception {
        check(!horse.isLeashed()&&owner.getInventory().getItemInMainHand().getAmount()==3,"cancelled leash consumes nothing");cancelLeash=false;allowed.set(false);modelClick(owner,horse);
        later(3,()->{check(!horse.isLeashed()&&owner.getInventory().getItemInMainHand().getAmount()==3,"protected leash consumes nothing");allowed.set(true);int before=leashEvents;modelClick(owner,horse);later(3,()->{
            check(horse.isLeashed()&&horse.getLeashHolder().equals(owner),"model click creates actual native lead: linked="+horse.isLeashed()+" qty="+owner.getInventory().getItemInMainHand()+" occupied="+HerdModels.ridden(horse)+" events="+leashEvents+" access="+animals.leadAccess(owner,horse)+" at="+horse.getLocation()+" holder="+(horse.isLeashed()?horse.getLeashHolder():null));
            check(owner.getInventory().getItemInMainHand().getAmount()==2&&leashEvents==before+1,"duplicated renderer/hand callbacks attach and consume once");
            check(!((HerdTempt.FoodGoal)Bukkit.getMobGoals().getGoal(horse,HerdTempt.FoodGoal.KEY)).accepts(owner),"leashed animal yields food goal to native lead");
            horse.setAI(true);var beforeMove=horse.getLocation();owner.teleport(beforeMove.clone().add(0,0,7));later(40,()->{
                check(horse.isLeashed()&&horse.getLocation().distanceSquared(beforeMove)>.05,"native lead physics moves animal toward holder");horse.setAI(false);owner.teleport(horse.getLocation().clone().add(0,0,3));cancelUnleash=true;modelClick(owner,horse);later(3,this::detach);
            });
        });});
    }
    private int droppedLeads(){return horse.getWorld().getEntitiesByClass(Item.class).stream().filter(i->i.getItemStack().getType()==Material.LEAD).mapToInt(i->i.getItemStack().getAmount()).sum();}
    private void detach() throws Exception {
        check(horse.isLeashed(),"cancelled detach preserves native lead");cancelUnleash=false;int before=droppedLeads();modelClick(owner,horse);later(3,()->{
            check(!horse.isLeashed()&&droppedLeads()==before+1,"detach returns exactly one lead");
            other.teleport(horse.getLocation().clone().add(1,0,2));other.getInventory().setItemInMainHand(new ItemStack(Material.LEAD,2));modelClick(other,horse);
            later(3,()->{check(!horse.isLeashed()&&other.getInventory().getItemInMainHand().getAmount()==2,"non-owner cannot leash owned animal");
                owner.teleport(wild.getLocation().clone().add(0,0,2));owner.getInventory().setItemInMainHand(new ItemStack(Material.AIR));owner.getInventory().setItemInOffHand(new ItemStack(Material.LEAD,2));modelClick(owner,wild);
                later(3,()->{check(wild.isLeashed()&&!wild.isTamed()&&owner.getInventory().getItemInOffHand().getAmount()==1,"wild Dewback supports offhand lead without taming");wild.setLeashHolder(null);
                    owner.teleport(baby.getLocation().clone().add(0,0,2));owner.getInventory().setItemInMainHand(new ItemStack(Material.LEAD,2));owner.getInventory().setItemInOffHand(new ItemStack(Material.AIR));modelClick(owner,baby);
                    later(3,()->{check(baby.isLeashed()&&owner.getInventory().getItemInMainHand().getAmount()==1,"owned baby accepts one lead");int leadCount=droppedLeads();baby.setHealth(0);check(droppedLeads()==leadCount,"baby death does not leak a separate native lead drop");fence();});
                });
            });
        });
    }
    private void fence() throws Exception {
        horse.teleport(point);owner.teleport(point.clone().add(0,0,3));owner.getInventory().setItemInMainHand(new ItemStack(Material.LEAD,2));modelClick(owner,horse);
        later(3,()->{
            var block=point.getWorld().getBlockAt(103,20,100);block.setType(Material.OAK_FENCE);
            bindFence(block);check(horse.isLeashed()&&horse.getLeashHolder() instanceof LeashHitch,"native fence interaction ties actual lead knot");
            check(owner.getInventory().getItemInMainHand().getAmount()==1,"fence transfer never consumes a second lead");
            Files.writeString(state,horse.getUniqueId()+"\n"+horse.getOwnerUniqueId());finish("LEADS-PASSED.txt");
        });
    }
    private void restart() throws Exception {
        var data=Files.readAllLines(state);horse=(Horse)Bukkit.getEntity(UUID.fromString(data.get(0)));
        check(horse!=null&&horse.isTamed()&&UUID.fromString(data.get(1)).equals(horse.getOwnerUniqueId()),"native owner persists after GUI and leads");
        check(owner.getUniqueId().equals(horse.getOwnerUniqueId()),"rejoining actor retains the original owner UUID");
        check(HerdModels.saddled(horse),"single native saddle persists after custom GUI");
        check(horse.isLeashed()&&horse.getLeashHolder() instanceof LeashHitch,"native fence tether survives restart");
        check(horse.getLeashHolder().getLocation().getBlock().getType()==Material.OAK_FENCE,"saved tether retains original fence");
        owner.teleport(horse.getLocation().clone().add(0,0,3));owner.getInventory().clear();owner.setSneaking(false);modelClick(owner,horse);
        later(3,()->{check(!horse.isLeashed(),"owner can recover saved native tether");owner.setSneaking(true);later(3,()->{modelClick(owner,horse);later(3,()->{check(HerdEquipment.isMenu(owner.getOpenInventory().getTopInventory(),horse),"custom GUI opens after restart and releasing lead");finish("LEADS-RESTART-PASSED.txt");});});});
    }
    private void bindFence(org.bukkit.block.Block block) throws Exception {
        var handle=owner.getClass().getMethod("getHandle").invoke(owner);var level=block.getWorld().getClass().getMethod("getHandle").invoke(block.getWorld());var pos=Class.forName("net.minecraft.core.BlockPos");
        Class.forName("net.minecraft.world.item.LeadItem").getMethod("bindPlayerMobs",Class.forName("net.minecraft.world.entity.player.Player"),Class.forName("net.minecraft.world.level.Level"),pos)
                .invoke(null,handle,level,pos.getConstructor(int.class,int.class,int.class).newInstance(block.getX(),block.getY(),block.getZ()));
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private void containerClick(Player player,int slot,String input) throws Exception {
        var handle=player.getClass().getMethod("getHandle").invoke(player);var menu=handle.getClass().getField("containerMenu").get(handle);var listener=handle.getClass().getField("connection").get(handle);
        var kind=Class.forName("net.minecraft.world.inventory.ContainerInput");var hashes=Class.forName("net.minecraft.network.HashedStack");var map=Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");var packet=Class.forName("net.minecraft.network.protocol.game.ServerboundContainerClickPacket");
        var message=packet.getConstructor(int.class,int.class,short.class,byte.class,kind,map,hashes).newInstance(menu.getClass().getField("containerId").getInt(menu),menu.getClass().getMethod("getStateId").invoke(menu),(short)slot,(byte)0,Enum.valueOf((Class)kind,input),new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(),hashes.getField("EMPTY").get(null));
        listener.getClass().getMethod("handleContainerClick",packet).invoke(listener,message);
    }
    private void modelClick(Player player,Horse horse) throws Exception {
        var model=ModelEngineAPI.getModeledEntity(horse).getModels().values().iterator().next();int id=((DisplayRenderer)model.getModelRenderer()).getHitbox().getHitboxId();
        var tracker=ModelEngineAPI.getInteractionTracker();Integer relay=tracker.getEntityRelay(id);if(relay==null&&tracker.getModelRelay(id)!=null)relay=horse.getEntityId();
        check(relay!=null,"actual model interaction relay exists");
        var handle=player.getClass().getMethod("getHandle").invoke(player);var listener=handle.getClass().getField("connection").get(handle);var hand=Class.forName("net.minecraft.world.InteractionHand");var vec=Class.forName("net.minecraft.world.phys.Vec3");var type=Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
        var packet=type.getConstructor(int.class,hand,vec,boolean.class).newInstance(id,hand.getField("MAIN_HAND").get(null),vec.getConstructor(double.class,double.class,double.class).newInstance(0,1,0),player.isSneaking());
        var wrapper=Class.forName("com.ticxo.modelengine.v26_1.network.patch.ServerboundInteractPacketWrapper",true,ModelEngineAPI.class.getClassLoader());var wrapped=wrapper.getConstructor(int.class,type).newInstance(relay,packet);wrapper.getMethod("handle",Class.forName("net.minecraft.network.protocol.game.ServerGamePacketListener")).invoke(wrapped,listener);
    }
    @EventHandler(priority=EventPriority.NORMAL) public void leash(PlayerLeashEntityEvent event){leashEvents++;if(cancelLeash)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.NORMAL) public void unleash(PlayerUnleashEntityEvent event){if(cancelUnleash)event.setCancelled(true);}
    private void finish(String name)throws Exception {rows.add("CHECKS "+checks);Files.write(getDataFolder().toPath().resolve(name),rows);Bukkit.shutdown();}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"GUI/LEAD PROBE FAILED",e);try{Files.writeString(getDataFolder().toPath().resolve("LEADS-FAILED.txt"),e.toString()+"\n"+String.join("\n",rows));}catch(Exception ignored){}Bukkit.shutdown();}
    public void onDisable(){if(actors!=null)actors.close();if(models!=null)models.close();}
}
