package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.spawners.*;
import net.mandomc.gameplay.items.*;
import net.mandomc.integrations.models.SpawnerModel;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnerPreviewProbe extends JavaPlugin {
    private MandoPlugin mando;private NativeProbePlayers actors;private Player player;private MobSpawners spawners;private World world;private int checks;private net.mandomc.gameplay.clans.ClanState.Clan testClan;
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    private void later(int t,Work w){Bukkit.getScheduler().runTaskLater(this,()->{try{w.run();}catch(Throwable e){finish(e);}},t);}
    private Object field(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void invoke(Object o,String n)throws Exception{var m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    @Override public void onEnable(){later(180,this::start);}
    private void start()throws Exception{
        Files.deleteIfExists(getDataFolder().toPath().resolve("PASSED.txt"));
        check(Bukkit.getPort()==25591,"Disposable runtime required");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled()&&!((boolean)field(mando,"failed")),"Mando enabled and healthy");
        spawners=(MobSpawners)field(mando,"spawners");world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(1024);
        for(int x=-14;x<-10;x++)for(int z=-14;z<-10;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        actors=new NativeProbePlayers("Preview");player=actors.create(new Location(world,-201.5,200,-199.5));player.setGameMode(GameMode.SURVIVAL);mando.ranks().apply(player);
        for(int x=-212;x<-172;x++)for(int z=-214;z<-192;z++){
            world.getBlockAt(x,199,z).setType(Material.STONE,false);
            for(int y=200;y<206;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        var ids=List.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner");
        for(int i=0;i<4;i++){
            String id=ids.get(i);var item=mando.items().create(id);var meta=(BlockStateMeta)item.getItemMeta();
            check(meta.hasBlockState()&&((CreatureSpawner)meta.getBlockState()).getSpawnedType()==null,"Shop item has no default pig "+id);
            var lore=meta.lore().stream().map(this::plain).toList();check(lore.stream().anyMatch(s->s.contains("your own clan")),"Clear claim wording "+id);
            var legacy=item.clone();legacy.setAmount(9);var old=(BlockStateMeta)legacy.getItemMeta();old.displayName(Component.text("Personal spawner"));
            var oldstate=(CreatureSpawner)old.getBlockState();oldstate.setSpawnedType(EntityType.PIG);old.setBlockState(oldstate);
            old.lore(List.of(Component.text("Custom note"),MiniMessage.miniMessage().deserialize("<yellow>Place in Wilderness. Break with a pickaxe to recover.")));legacy.setItemMeta(old);
            check(ItemPresentationRefresh.refresh(mando.items(),legacy),"Legacy item migrates "+id);
            check(legacy.getAmount()==9&&plain(legacy.getItemMeta().displayName()).equals("Personal spawner"),"Migration preserves name/count "+id);
            check(plain(legacy.getItemMeta().lore().getFirst()).equals("Custom note")&&legacy.getItemMeta().lore().stream().anyMatch(c->plain(c).contains("your own clan")),"Migration preserves custom lore and adds claim hint "+id);
            check(!ItemPresentationRefresh.refresh(mando.items(),legacy),"Migration is idempotent "+id);
            var b=world.getBlockAt(-206+i*7,200,-204);var before=b.getState();b.setType(Material.SPAWNER,false);
            var e=new BlockPlaceEvent(b,before,b.getRelative(0,-1,0),item,player,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(e);
            check(!e.isCancelled(),"Native custom placement allowed "+id);check(((CreatureSpawner)b.getState()).getSpawnedType()==null,"Placed cage has no pig "+id);
        }
        // A persisted old spawner is repaired on load without replacement or inventory consumption.
        var legacyBlock=world.getBlockAt(-207,200,-196);legacyBlock.setType(Material.SPAWNER,false);var legacyState=(CreatureSpawner)legacyBlock.getState();
        legacyState.getPersistentDataContainer().set(MobSpawners.TYPE,PersistentDataType.STRING,"massiff_spawner");legacyState.setSpawnedType(EntityType.PIG);legacyState.update();
        var load=MobSpawners.class.getDeclaredMethod("load",Chunk.class);load.setAccessible(true);load.invoke(spawners,legacyBlock.getChunk());
        check(((CreatureSpawner)legacyBlock.getState()).getSpawnedType()==null,"Existing pig block migrates on load");
        check(SpawnerModel.available("massiff"),"Massiff blueprint ready");invoke(spawners,"tick");later(25,this::verifyPreviews);
    }
    private void verifyPreviews()throws Exception{
        var previews=(Map<?,?>)field(spawners,"previews");check(previews.size()>=5,"One visual and hologram per nearby spawner: previews="+previews.size()+" sites="+field(spawners,"sites")+" player="+player.getLocation()+" online="+player.isOnline()+" worldPlayers="+world.getPlayers().size());
        var models=new HashSet<String>();var labels=new HashSet<String>();
        for(var p:previews.values()){
            var base=(ArmorStand)field(p,"base");var label=(TextDisplay)field(p,"label");var model=(SpawnerModel)field(p,"model");
            if(base.getWorld()!=world)continue;
            check(base.isMarker()&&!base.isVisible()&&!base.hasGravity()&&!base.isPersistent(),"Preview body invisible, inert and unsaved");
            check(!label.isPersistent()&&label.getBillboard()==Display.Billboard.CENTER,"Hologram unsaved and faces viewer");
            check(model.valid()&&!model.active().canHurt()&&!model.active().isMainHitbox(),"Miniature has no combat or hitbox");
            String name=model.active().getBlueprint().getName();models.add(name);labels.add(plain(label.text()));
            check(model.active().getScale().x()>0&&model.active().getScale().x()<1,"Miniature scaled to fit cage "+name);
        }
        check(models.containsAll(Set.of("massiff","dewback","bantha","jawa_brown")),"All four matching creature rigs");
        check(labels.containsAll(Set.of("1x Massiff","1x Dewback","1x Bantha","1x Jawa")),"All four requested hologram labels");
        int count=previews.size();invoke(spawners,"tick");
        check(previews.size()==count,"Repeated tracking does not duplicate holograms");
        var chunk=world.getBlockAt(-206,200,-204).getChunk();
        spawners.unloaded(new org.bukkit.event.world.ChunkUnloadEvent(chunk,true));
        check(previews.size()<count,"Chunk unload removes owned previews");
        var load=MobSpawners.class.getDeclaredMethod("load",Chunk.class);load.setAccessible(true);load.invoke(spawners,chunk);for(int i=0;i<20;i++)invoke(spawners,"tick");
        check(previews.size()==count,"Chunk reload restores one preview per spawner");
        var first=world.getBlockAt(-206,200,-204);int before=previews.size();var e=new BlockBreakEvent(first,player);player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));Bukkit.getPluginManager().callEvent(e);check(!e.isCancelled(),"Spawner pickaxe recovery remains permitted");first.setType(Material.AIR,false);
        check(previews.size()==before-1,"Breaking spawner immediately removes its visual and label");
        // Same underlying Wilderness, with a real clan membership/claim in the native state.
        var clans=Bukkit.getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);var clan=testClan=clans.state().create("Preview"+UUID.randomUUID().toString().substring(0,8),player.getUniqueId(),System.currentTimeMillis());
        var location=world.getBlockAt(-199,200,-204).getLocation();
        clans.state().claims.put(net.mandomc.gameplay.clans.LandRestoration.chunk(location),clan.id);
        check(clans.owner(location)==clan&&clans.canEdit(player,location),"Own native clan claim remains editable");
        var block=world.getBlockAt(-199,201,-204);var original=block.getState();block.setType(Material.SPAWNER,false);var place=new BlockPlaceEvent(block,original,block.getRelative(0,-1,0),mando.items().create("massiff_spawner"),player,true,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(place);check(!place.isCancelled(),"Spawner placement allowed inside own clan claim");
        spawners.close();check(previews.isEmpty(),"Shutdown removes all preview ownership");
        check(world.getEntities().stream().noneMatch(x->x.getPersistentDataContainer().has(new NamespacedKey("mandomc","spawner_preview_v1"))),"No model bodies or labels remain after shutdown");
        finish(null);
    }
    private void finish(Throwable error){try{var clans=Bukkit.getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);if(testClan!=null&&clans!=null)clans.state().remove(testClan);if(actors!=null)actors.close();}catch(Throwable e){if(error==null)error=e;}
        if(error==null){getLogger().info("SPAWNER PREVIEW PASSED: "+checks+" assertions");try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),checks+" native assertions\n");}catch(Exception e){throw new RuntimeException(e);}}
        else getLogger().log(java.util.logging.Level.SEVERE,"SPAWNER PREVIEW FAILED",error);Bukkit.shutdown();}
}
