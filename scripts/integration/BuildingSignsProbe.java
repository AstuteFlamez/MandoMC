package net.mandomc.verification;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.trophies.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

/** Native placement and actual JVM restart checks, exclusively in the disposable port-25593 fixture. */
public final class BuildingSignsProbe extends JavaPlugin implements Listener {
    private final String signId=System.getProperty("mandomc.test.sign","shops_sign");
    private final SignShape shape=Objects.requireNonNull(SignShape.forItem(signId));
    private MandoPlugin mando;private RancorTrophies trophies;private NativeProbePlayers actors;
    private Player owner;private World world;private Block site;private ItemStack item;
    private final Map<UUID,Object> handles=new HashMap<>();private final List<Chunk> chunks=new ArrayList<>();
    private int checks,index;private boolean denyPlace,denyBreak,finished;
    private static final BlockFace[] FACES={BlockFace.SOUTH,BlockFace.NORTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN};
    private interface Work{void run()throws Exception;}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private void later(long ticks,Work action){Bukkit.getScheduler().runTaskLater(this,()->{if(finished)return;try{action.run();}catch(Throwable failure){finished=true;getLogger().log(java.util.logging.Level.SEVERE,"BUILDING SIGN FAILED",failure);Bukkit.shutdown();}},ticks);}
    private void await(BooleanSupplier condition,Work action){await(condition,action,0);}
    private void await(BooleanSupplier condition,Work action,int tries){later(2,()->{if(condition.getAsBoolean())action.run();else if(tries>150)throw new AssertionError("Custody transition timeout at cycle "+index);else await(condition,action,tries+1);});}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(200,this::begin);}
    private void begin()throws Exception{
        check(getServer().getPort()==25593,"isolated runtime required");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        trophies=Bukkit.getServicesManager().load(RancorTrophies.class);check(trophies!=null,"custody owner loaded");world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        for(int x=0;x<=1;x++)for(int z=0;z<=1;z++){var chunk=world.getChunkAt(x,z);chunk.addPluginChunkTicket(this);chunks.add(chunk);}
        site=world.getBlockAt(15,200,15);actors=new NativeProbePlayers("SignProbe");var restart=new java.io.File(getDataFolder(),"restart.yml");
        var saved=YamlConfiguration.loadConfiguration(restart);owner=restart.exists()?actors.create(site.getLocation().add(.5,0,3.5),UUID.fromString(saved.getString("owner"))):actors.create(site.getLocation().add(.5,0,3.5));
        owner.setGameMode(GameMode.SURVIVAL);handles.put(owner.getUniqueId(),owner.getClass().getMethod("getHandle").invoke(owner));
        if(restart.exists()){
            owner.loadData();moveActor(site.getLocation().add(.5,0,3.5));item=ItemStack.deserializeBytes(Base64.getDecoder().decode(saved.getString("item")));
            var e=trophies.journal().entries().get(UUID.fromString(saved.getString("entry")));check(e!=null&&e.width()==shape.width()&&e.height()==shape.height()&&e.phase()==Phase.PLACED,"three-cell sign survives JVM restart");
            later(25,()->{inspect(e);nativeBreak(block(e.cells().getLast()));await(()->entry()==null&&count()==1,()->{check(trophies.journal().entries().isEmpty(),"restart return retires custody");check(owner.getInventory().getItemInMainHand().isSimilar(item),"restart preserves metadata");trophies.recover(owner);later(25,()->{check(count()==1,"recovery returns exactly once");finish("BUILDING SIGN RESTART PASSED");});});});return;
        }
        owner.getInventory().clear();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mmc give "+owner.getName()+" "+signId+" 1");item=owner.getInventory().getItemInMainHand().clone();check(trophies.sign(item)&&count()==1,"actual mmc give creates sign");
        var meta=item.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Crates test metadata"));item.setItemMeta(meta);
        check(!trophies.sign(new ItemStack(Material.PAPER)),"ordinary paper excluded");cycle();
    }
    private Block block(Cell c){return world.getBlockAt(c.x(),c.y(),c.z());}
    private Entry entry(){return trophies.journal().at(new Cell(world.getKey().toString(),site.getX(),site.getY(),site.getZ()));}
    private long count(){return Arrays.stream(owner.getInventory().getStorageContents()).filter(Objects::nonNull).filter(trophies::sign).mapToInt(ItemStack::getAmount).sum();}
    private List<ItemDisplay> visuals(){return world.getNearbyEntities(site.getLocation().add(.5,.5,.5),1,4,1).stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();}
    private void prepare(BlockFace face)throws Exception{
        for(int x=13;x<=17;x++)for(int z=13;z<=17;z++)for(int y=196;y<=205;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        site.getRelative(face.getOppositeFace()).setType(Material.STONE,false);owner.getInventory().clear();owner.getInventory().setItemInMainHand(item.clone());moveActor(site.getLocation().add(.5,0,3.5));
    }
    private void inspect(Entry e){
        check(e.width()==shape.width()&&e.height()==shape.height()&&e.cells().size()==shape.width()*shape.height(),"three cells reserved");
        for(var c:e.cells()){check(block(c).getType()==Material.BARRIER,"every occupied cell collides");check(trophies.journal().at(c).id().equals(e.id()),"each part maps to same sign");}
        check(visuals().size()==1,"exactly one model");var d=visuals().getFirst();check(d.getItemStack().isSimilar(item),"original metadata displayed");check(d.getItemDisplayTransform()==ItemDisplay.ItemDisplayTransform.NONE,"full sized untransformed model");
        check(d.getYaw()==(e.yaw()>180?e.yaw()-360:e.yaw())||d.getYaw()==e.yaw(),"display rotation follows wall");check(d.getPitch()==0,"one block tall even on ceiling");check(d.getTransformation().getTranslation().y()==shape.verticalOffset()&&d.getTransformation().getScale().x()==shape.modelScale(),"model sits on block boundary");
    }
    private void cycle()throws Exception{
        if(index==FACES.length){blocked();return;}var face=FACES[index];prepare(face);nativeUse(site.getRelative(face.getOppositeFace()),face);
        await(()->entry()!=null&&entry().phase()==Phase.PLACED&&!visuals().isEmpty(),()->{
            var e=entry();inspect(e);check(count()==0,"one item consumed");check(e.face()==Face.valueOf(face.name()),"clicked face retained");
            var cells=e.cells();nativeBreak(block(cells.get(index%cells.size())));await(()->entry()==null&&count()==1,()->{
                for(var c:cells)check(block(c).getType().isAir(),"all three parts removed");check(visuals().isEmpty(),"visual removed");index++;cycle();
            });
        });
    }
    @EventHandler(priority=EventPriority.HIGH) public void placement(BlockPlaceEvent e){if(denyPlace&&e.getBlock().equals(edge()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGH) public void breaking(BlockBreakEvent e){if(denyBreak&&e.getBlock().equals(edge()))e.setCancelled(true);}
    private Block edge(){return shape.height()>1?site.getRelative(BlockFace.UP,shape.height()-1):site.getRelative(BlockFace.EAST,shape.width()/2);}
    private void blocked()throws Exception{
        prepare(BlockFace.SOUTH);var edge=edge();edge.setType(Material.STONE,false);trophies.place(owner,site,EquipmentSlot.HAND,Face.SOUTH,site.getRelative(BlockFace.NORTH));
        check(entry()==null&&count()==1&&edge.getType()==Material.STONE,"blocked outer cell preserves inventory and terrain");edge.setType(Material.AIR,false);
        denyPlace=true;trophies.place(owner,site,EquipmentSlot.HAND,Face.SOUTH,site.getRelative(BlockFace.NORTH));
        later(40,()->{check(entry()==null&&count()==1&&edge.getType().isAir()&&site.getType().isAir(),"protection on outer cell denies entire sign and refunds");denyPlace=false;
            trophies.place(owner,site,EquipmentSlot.HAND,Face.SOUTH,site.getRelative(BlockFace.NORTH));await(()->entry()!=null&&entry().phase()==Phase.PLACED&&!visuals().isEmpty(),()->{
                var e=entry();denyBreak=true;nativeBreak(site);later(10,()->{check(entry()!=null&&count()==0,"protected outer cell prevents breaking center");denyBreak=false;
                    var blocks=new ArrayList<Block>();for(var c:e.cells())blocks.add(block(c));trophies.entityExplosion(new EntityExplodeEvent(owner,site.getLocation(),blocks,1,ExplosionResult.DESTROY));check(blocks.isEmpty(),"explosion protects all cells");
                    var piston=new BlockPistonExtendEvent(edge.getRelative(BlockFace.EAST),List.of(edge),BlockFace.WEST);trophies.piston(piston);check(piston.isCancelled(),"piston cannot move outer cell");
                    for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Material.STONE,64));nativeBreak(edge);
                    await(()->site.getType().isAir()&&entry()==null,()->{check(trophies.journal().entries().values().stream().anyMatch(v->v.phase()==Phase.RETURNING),"full inventory queues single return");owner.getInventory().setItem(0,null);trophies.recover(owner);
                        await(()->count()==1&&trophies.journal().entries().isEmpty(),this::persist);
                    });
                });
            });
        });
    }
    private void persist()throws Exception{
        prepare(BlockFace.EAST);trophies.place(owner,site,EquipmentSlot.HAND,Face.EAST,site.getRelative(BlockFace.WEST));
        await(()->entry()!=null&&entry().phase()==Phase.PLACED&&!visuals().isEmpty(),()->{
            var data=new YamlConfiguration();data.set("owner",owner.getUniqueId().toString());data.set("entry",entry().id().toString());data.set("item",Base64.getEncoder().encodeToString(item.serializeAsBytes()));data.save(new java.io.File(getDataFolder(),"restart.yml"));owner.saveData();finish("BUILDING SIGN LIFECYCLE PASSED");
        });
    }
    private void finish(String message){getLogger().info(message+" "+signId+" "+checks);finished=true;Bukkit.shutdown();}
    @Override public void onDisable(){HandlerList.unregisterAll((Listener)this);if(actors!=null)actors.close();chunks.forEach(c->c.removePluginChunkTicket(this));}
    private void moveActor(Location location)throws Exception{
        // Unregistered fixture players have no client connection to acknowledge Bukkit teleports.
        var handle=handles.get(owner.getUniqueId());handle.getClass().getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());handle.getClass().getMethod("setYRot",float.class).invoke(handle,location.getYaw());handle.getClass().getMethod("setXRot",float.class).invoke(handle,location.getPitch());check(owner.getLocation().distanceSquared(location)<1e-6,"fixture actor is actually within native reach");
    }
    private void nativeUse(Block clicked,BlockFace face)throws Exception{
        var handle=handles.get(owner.getUniqueId());var mode=handle.getClass().getField("gameMode").get(handle);var posType=Class.forName("net.minecraft.core.BlockPos");var dirType=Class.forName("net.minecraft.core.Direction");var handType=Class.forName("net.minecraft.world.InteractionHand");var vecType=Class.forName("net.minecraft.world.phys.Vec3");var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(clicked.getX(),clicked.getY(),clicked.getZ());var at=clicked.getLocation().add(.5,.5,.5).add(face.getDirection().multiply(.5));if(clicked.getType()==Material.STONE_SLAB&&face==BlockFace.UP)at.setY(clicked.getY()+.5);var vec=vecType.getConstructor(double.class,double.class,double.class).newInstance(at.getX(),at.getY(),at.getZ());var direction=dirType.getField(face.name()).get(null);var hand=handType.getField("MAIN_HAND").get(null);var hit=hitType.getConstructor(vecType,dirType,posType,boolean.class).newInstance(vec,direction,pos,false);var item=handle.getClass().getMethod("getItemInHand",handType).invoke(handle,hand);var level=world.getClass().getMethod("getHandle").invoke(world);Arrays.stream(mode.getClass().getMethods()).filter(m->m.getName().equals("useItemOn")&&m.getParameterCount()==5).findFirst().orElseThrow().invoke(mode,handle,level,item,hand,hit);
    }
    private void nativeBreak(Block block)throws Exception{
        var handle=handles.get(owner.getUniqueId());var mode=handle.getClass().getField("gameMode").get(handle);var posType=Class.forName("net.minecraft.core.BlockPos");var dirType=Class.forName("net.minecraft.core.Direction");var actionType=Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(block.getX(),block.getY(),block.getZ());mode.getClass().getMethod("handleBlockBreakAction",posType,actionType,dirType,int.class,int.class).invoke(mode,pos,actionType.getField("START_DESTROY_BLOCK").get(null),dirType.getField("UP").get(null),world.getMaxHeight(),1);
    }
}
