package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.spawners.MobSpawners;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native events, live Mythic spawning, lossless pickup and actual-restart count checks. */
public final class SpawnerStackProbe extends JavaPlugin {
    private final List<String> ids=List.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner");
    private final List<ClanState.Clan> testClans=new ArrayList<>();
    private MandoPlugin mando;private MobSpawners spawners;private NativeProbePlayers actors;private Player player;
    private ClanService clans;private World world;private int checks;
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void tick()throws Exception{var m=MobSpawners.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(spawners);}
    private Block block(int i){return world.getBlockAt(-206+i*7,200,-204);}
    private int count(int i){return MobSpawners.count((CreatureSpawner)block(i).getState());}
    private void hold(String id,int amount){var item=mando.items().create(id);item.setAmount(amount);player.getInventory().setItemInMainHand(item);}
    private int held(){return player.getInventory().getItemInMainHand().getAmount();}
    private PlayerInteractEvent click(int i,EquipmentSlot hand){
        var e=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInMainHand(),block(i),org.bukkit.block.BlockFace.UP,hand);
        Bukkit.getPluginManager().callEvent(e);return e;
    }
    private void near(int i){player.teleport(block(i).getLocation().add(.5,0,2.5));player.setSneaking(true);}
    private void label(int i,int count)throws Exception{
        String expected=count+"x "+List.of("Massiff","Dewback","Bantha","Jawa").get(i);
        check(world.getEntitiesByClass(TextDisplay.class).stream().anyMatch(d->d.getLocation().distanceSquared(block(i).getLocation().add(.5,1.2,.5))<.01
                &&PlainTextComponentSerializer.plainText().serialize(d.text()).equals(expected)),"Hologram shows "+expected);
    }
    @Override public void onEnable(){later(180,this::start);}
    private void start()throws Exception{
        Files.deleteIfExists(getDataFolder().toPath().resolve("PASSED.txt"));
        check(Bukkit.getPort()==25591,"Disposable fixture only");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando!=null&&mando.isEnabled()&&!(boolean)field(mando,"failed"),"Gameplay startup healthy");
        clans=Bukkit.getServicesManager().load(ClanService.class);check(clans!=null&&clans.available(),"Native clans ready");
        spawners=(MobSpawners)field(mando,"spawners");world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        for(int x=-14;x<-10;x++)for(int z=-14;z<-10;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        actors=new NativeProbePlayers("Stack");player=actors.create(new Location(world,-201.5,200,-199.5));player.setGameMode(GameMode.SURVIVAL);mando.ranks().apply(player);
        if(Boolean.getBoolean("mandomc.stack.restart")){
            for(int i=1;i<4;i++)check(count(i)==4,"Combined count persists through JVM restart "+ids.get(i));
            tick();later(5,()->{for(int i=1;i<4;i++)label(i,4);finish(null);});return;
        }
        var mobs=new net.mandomc.integrations.mobs.MythicMobAccess();
        for(var entity:world.getEntities())if(entity instanceof Item||net.mandomc.integrations.mobs.MythicMobAccess.TYPES.contains(mobs.type(entity)))entity.remove();
        for(int x=-214;x<-172;x++)for(int z=-214;z<-192;z++){
            world.getBlockAt(x,199,z).setType(Material.STONE,false);
            for(int y=200;y<207;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        for(int i=0;i<4;i++){
            var old=mando.items().create(ids.get(i));old.setAmount(7);var meta=old.getItemMeta();
            meta.displayName(net.kyori.adventure.text.Component.text("Named cage"));
            meta.lore(List.of(net.kyori.adventure.text.Component.text("Player note"),net.kyori.adventure.text.Component.text("Place in Wilderness. Break with a pickaxe to recover.")));old.setItemMeta(meta);
            check(net.mandomc.gameplay.items.ItemPresentationRefresh.refresh(mando.items(),old),"Legacy controls migrate "+ids.get(i));
            var lines=old.getItemMeta().lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
            check(old.getAmount()==7&&PlainTextComponentSerializer.plainText().serialize(old.getItemMeta().displayName()).equals("Named cage")
                    &&lines.contains("Player note")&&lines.contains("Place in unclaimed Wilderness")&&lines.contains("Shift-right-click to combine matching spawners."),"Migration preserves notes/name/count and complete controls "+ids.get(i));
            check(!net.mandomc.gameplay.items.ItemPresentationRefresh.refresh(mando.items(),old),"Control migration idempotent "+ids.get(i));
            var recent=mando.items().create(ids.get(i));var recentMeta=recent.getItemMeta();var recentLore=new ArrayList<>(recentMeta.lore());recentLore.removeLast();recentMeta.lore(recentLore);recent.setItemMeta(recentMeta);
            check(net.mandomc.gameplay.items.ItemPresentationRefresh.refresh(mando.items(),recent),"Previous release receives combine hint "+ids.get(i));
            var b=block(i);var before=b.getState();b.setType(Material.SPAWNER,false);
            var event=new BlockPlaceEvent(b,before,b.getRelative(0,-1,0),mando.items().create(ids.get(i)),player,true,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled()&&count(i)==1,"New cage starts at one "+ids.get(i));
            var oldState=(CreatureSpawner)b.getState();oldState.getPersistentDataContainer().remove(MobSpawners.COUNT);oldState.update();check(count(i)==1,"Old cage defaults to one "+ids.get(i));
        }
        tick();combineType(0);
    }
    private void combineType(int i)throws Exception{
        if(i==4){normalAndOffhand();return;}
        near(i);hold(ids.get(i),3);var event=click(i,EquipmentSlot.HAND);
        check(event.useItemInHand()==Event.Result.DENY,"Combine blocks adjacent native placement "+ids.get(i));
        click(i,EquipmentSlot.HAND); // Duplicate packet before either deferred transaction commits.
        later(2,()->{check(count(i)==4&&player.getInventory().getItemInMainHand().isEmpty(),"Exactly-once held-stack merge "+ids.get(i));label(i,4);combineType(i+1);});
    }
    private void normalAndOffhand()throws Exception{
        near(0);hold(ids.getFirst(),2);player.setSneaking(false);var normal=click(0,EquipmentSlot.HAND);
        check(normal.useItemInHand()!=Event.Result.DENY,"Normal click retains ordinary placement behavior");
        player.setSneaking(true);click(0,EquipmentSlot.OFF_HAND);
        later(2,()->{check(count(0)==4&&held()==2,"Offhand event never duplicates merge");hold(ids.get(1),3);click(0,EquipmentSlot.HAND);
            later(2,()->{check(count(0)==4&&held()==3,"Different creature types cannot combine");protection();});});
    }
    private void protection()throws Exception{
        hold(ids.getFirst(),2);
        var denied=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInMainHand(),block(0),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        denied.setCancelled(true);Bukkit.getPluginManager().callEvent(denied);
        var late=click(0,EquipmentSlot.HAND);late.setUseInteractedBlock(Event.Result.DENY);
        later(2,()->{check(count(0)==4&&held()==2,"Early and late protection denial preserves items/count");
            var own=clans.state().create("Stack"+UUID.randomUUID().toString().substring(0,8),player.getUniqueId(),System.currentTimeMillis());testClans.add(own);
            clans.state().claims.put(LandRestoration.chunk(block(0)),own.id);click(0,EquipmentSlot.HAND);
            later(2,()->{check(count(0)==6&&player.getInventory().getItemInMainHand().isEmpty(),"Own clan may combine spawners");
                var rival=clans.state().create("Rival"+UUID.randomUUID().toString().substring(0,8),UUID.randomUUID(),System.currentTimeMillis());testClans.add(rival);
                clans.state().claims.put(LandRestoration.chunk(block(0)),rival.id);hold(ids.getFirst(),2);click(0,EquipmentSlot.HAND);
                later(2,()->{check(count(0)==6&&held()==2,"Rival territory cannot combine or consume items");clans.state().claims.remove(LandRestoration.chunk(block(0)));capacity();});
            });
        });
    }
    private void capacity()throws Exception{
        hold(ids.getFirst(),64);click(0,EquipmentSlot.HAND);
        later(2,()->{check(count(0)==64&&held()==6,"Limit accepts only remaining capacity and preserves excess");label(0,64);click(0,EquipmentSlot.HAND);
            later(2,()->{check(count(0)==64&&held()==6,"Full cage consumes nothing");
                click(0,EquipmentSlot.HAND);player.getInventory().setItemInMainHand(mando.items().create(ids.get(1)));
                later(2,()->{check(count(0)==64&&ids.get(1).equals(mando.items().identify(player.getInventory().getItemInMainHand())),"Changing held item cannot commit stale transfer");spawnBudget();});
            });
        });
    }
    @SuppressWarnings("unchecked") private void spawnBudget()throws Exception{
        near(0);var mobs=new net.mandomc.integrations.mobs.MythicMobAccess();
        for(var entity:world.getEntities())if("Massiff".equals(mobs.type(entity)))mobs.remove(entity);
        var sites=(Map<Object,Integer>)field(spawners,"sites");sites.replaceAll((site,due)->0);tick();
        long spawned=world.getLivingEntities().stream().filter(e->"Massiff".equals(mobs.type(e))).count();
        check(spawned>1&&spawned<=6,"Stack creates multiple real creatures within existing shared cap: "+spawned);
        sites.replaceAll((site,due)->0);tick();long after=world.getLivingEntities().stream().filter(e->"Massiff".equals(mobs.type(e))).count();
        check(after<=6,"Repeated stacked spawning never exceeds nearby cap");
        var chunk=block(0).getChunk();spawners.unloaded(new org.bukkit.event.world.ChunkUnloadEvent(chunk,true));spawners.loaded(new org.bukkit.event.world.ChunkLoadEvent(chunk,false));tick();
        check(count(0)==64,"Combined count survives chunk tracking reload");label(0,64);
        var before=world.getEntitiesByClass(Item.class).stream().filter(e->ids.getFirst().equals(mando.items().identify(e.getItemStack()))).mapToInt(e->e.getItemStack().getAmount()).sum();
        var broken=new BlockBreakEvent(block(0),player);player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));Bukkit.getPluginManager().callEvent(broken);
        check(!broken.isCancelled()&&!broken.isDropItems(),"Native break delegates lossless custom recovery");block(0).setType(Material.AIR,false);
        later(2,()->{int recovered=world.getEntitiesByClass(Item.class).stream().filter(e->ids.getFirst().equals(mando.items().identify(e.getItemStack()))).mapToInt(e->e.getItemStack().getAmount()).sum();
            check(recovered-before==64,"Pickaxe returns all 64 separate spawner items");finish(null);});
    }
    private void finish(Throwable error){try{if(clans!=null)for(var clan:testClans)clans.state().remove(clan);if(actors!=null)actors.close();}catch(Throwable cleanup){if(error==null)error=cleanup;}
        if(error==null){String result=checks+" native assertions";getLogger().info("SPAWNER STACK PASSED: "+result);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result+"\n");}catch(Exception e){throw new RuntimeException(e);}}
        else getLogger().log(java.util.logging.Level.SEVERE,"SPAWNER STACK FAILED",error);Bukkit.shutdown();
    }
}
