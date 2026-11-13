package net.mandomc.verification;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.trophies.*;
import net.mandomc.gameplay.clans.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

/** Disposable runtime only. Two real starts exercise persisted world/player custody; never install live. */
public final class RancorTrophyProbe extends JavaPlugin implements Listener {
    private MandoPlugin mando;private RancorTrophies trophies;private ClanService clans;private World world;private Player owner,intruder,recruit;
    private Block site;private ItemStack skull;private int checks,placeEvents,realDeaths,attempts,playerDeathSkulls;private UUID bossId;private MythicMobDeathEvent lastDeath;
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();private final List<Entity> entities=new ArrayList<>();
    private final Map<World,List<Object>> worldPlayers=new HashMap<>();private Map<UUID,Object> online;private final List<Chunk> chunks=new ArrayList<>();
    private final NamespacedKey receiptKey=new NamespacedKey("mandomc","trophy_receipts_v1");private boolean cancelPlace,cancelDeath;
    private java.io.File state(){return new java.io.File(getDataFolder(),"restart.yml");}
    @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);later(160,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception{
        check(getServer().getPort()==25593,"isolated runtime enforced");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled(),"current combined MandoMC enabled");
        trophies=Bukkit.getServicesManager().load(RancorTrophies.class);check(trophies!=null,"trophy custody service registered");clans=Bukkit.getServicesManager().load(ClanService.class);check(clans!=null&&clans.available(),"current clan authority available");
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));for(int x=-15;x<=-12;x++)for(int z=-15;z<=-12;z++)ticket(world.getChunkAt(x,z));
        if(new java.io.File(getDataFolder(),"faces-only.txt").exists()){facesBegin();return;}
        if(new java.io.File(getDataFolder(),"input-only.txt").exists()){inputOnly();return;}
        if(state().exists()){restart();return;}
        owner=actor(new Location(world,-233.5,200,-231.5),UUID.randomUUID(),"SkullOwner",false);intruder=actor(new Location(world,-233.5,200,-231.5),UUID.randomUUID(),"SkullOther",false);recruit=actor(new Location(world,-233.5,200,-231.5),UUID.randomUUID(),"SkullRecruit",false);
        skull=mando.items().create(RancorTrophies.ITEM);check(skull!=null&&skull.getItemMeta().getItemModel().toString().equals("mandomc:trophies/rancor_skull"),"catalog resolves normal skull model");
        var lore=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(skull.getItemMeta().lore().getFirst());
        var config=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"items/catalog.yml"));check(lore.contains(config.getString("rarities.legendary")),"catalog uses Legendary badge");
        boss=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob("Rancor",new Location(world,-209,220,-209),1);bossId=boss.getUniqueId();entities.add(boss);boss.setGravity(false);boss.setAI(false);
        var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);var alive=new MythicMobDeathEvent(active,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(alive);check(alive.getDrops().stream().noneMatch(trophies::skull),"alive boss cannot be farmed with a synthetic death notification");
        cancelDeath=true;boss.damage(100000,owner);cancelDeath=false;check(!boss.isDead(),"cancelled lethal damage leaves boss alive");
        boss.setHealth(0);later(4,()->{check(realDeaths==1&&lastDeath!=null,"one actual Rancor death observed");check(lastDeath.getDrops().stream().filter(trophies::skull).mapToInt(ItemStack::getAmount).sum()==1,"real boss drops exactly one skull");check(lastDeath.getDrops().stream().anyMatch(s->s.getType()==Material.AMETHYST_SHARD),"existing death loot is preserved");
            Bukkit.getPluginManager().callEvent(lastDeath);check(lastDeath.getDrops().stream().filter(trophies::skull).mapToInt(ItemStack::getAmount).sum()==1,"replayed death cannot append a second skull");
            for(var item:world.getEntitiesByClass(Item.class))if(trophies.skull(item.getItemStack())||item.getItemStack().getType()==Material.AMETHYST_SHARD)item.remove();
            var meta=skull.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("My First Hunt"));meta.getPersistentDataContainer().set(new NamespacedKey("trophyprobe","memory"),PersistentDataType.STRING,"keep exactly");meta.setMaxStackSize(1);skull.setItemMeta(meta);
            negatives();
        });
    }
    private LivingEntity boss;
    @EventHandler(priority=EventPriority.LOW) public void originalLoot(MythicMobDeathEvent e){if(e.getEntity().getUniqueId().equals(bossId))e.getDrops().add(new ItemStack(Material.AMETHYST_SHARD));}
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){if(e.getEntity().getUniqueId().equals(bossId))realDeaths++;}
    @EventHandler(priority=EventPriority.MONITOR) public void mythicDeath(MythicMobDeathEvent e){if(e.getEntity().getUniqueId().equals(bossId)&&e.getEntity() instanceof LivingEntity l&&l.getHealth()<=0)lastDeath=e;}
    @EventHandler(priority=EventPriority.HIGHEST) public void cancelledHit(EntityDamageEvent e){if(cancelDeath&&e.getEntity().getUniqueId().equals(bossId))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cancelledPlace(BlockPlaceEvent e){if(trophies!=null&&trophies.skull(e.getItemInHand())){attempts++;if(cancelPlace)e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void placed(BlockPlaceEvent e){if(trophies!=null&&trophies.skull(e.getItemInHand()))placeEvents++;}
    @EventHandler(priority=EventPriority.MONITOR) public void playerDeath(PlayerDeathEvent e){if(e.getEntity().getName().equals("SkullDeath"))playerDeathSkulls=e.getDrops().stream().filter(trophies::skull).mapToInt(ItemStack::getAmount).sum();}
    private void finalWorldChecks()throws Exception{
        var b=floor(-226,-226);var id=UUID.randomUUID();b.setType(Material.BARRIER,false);var key=new NamespacedKey("mandomc","trophy_"+(b.getX()&15)+"_"+b.getY()+"_"+(b.getZ()&15));b.getChunk().getPersistentDataContainer().set(key,PersistentDataType.STRING,id.toString());
        var ghost=world.spawn(b.getLocation().add(.5,.5,.5),ItemDisplay.class,d->{d.setItemStack(skull.clone());d.getPersistentDataContainer().set(new NamespacedKey("mandomc","rancor_trophy_display_v1"),PersistentDataType.STRING,id.toString());});
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(b.getChunk(),false));Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.EntitiesLoadEvent(b.getChunk(),List.of(ghost)));
        check(b.getType().isAir()&&!ghost.isValid(),"chunk load clears retired world anchor/visual without another collectible item");
        var dead=actor(new Location(world,-219.5,200,-219.5),UUID.randomUUID(),"SkullDeath",false);dead.getInventory().clear();dead.getInventory().setItem(0,skull.clone());world.setGameRule(GameRules.KEEP_INVENTORY,false);dead.setHealth(0);
        check(playerDeathSkulls==1,"ordinary player death drops the carried trophy exactly once");check(trophies.journal().entries().isEmpty(),"ordinary death does not create a second recovery claim");
        getLogger().info("RANCOR TROPHY PROBE PASSED: "+checks+" assertions");Bukkit.shutdown();
    }
    private void ticket(Chunk c){c.addPluginChunkTicket(this);chunks.add(c);}
    private Block floor(int x,int z){var b=world.getBlockAt(x,200,z);b.setType(Material.AIR,false);b.getRelative(BlockFace.DOWN).setType(Material.STONE,false);return b;}
    private void give(Player p){p.getInventory().clear();p.getInventory().setItemInMainHand(skull.clone());}
    private void click(Player p,Block b){if(b.getRelative(BlockFace.DOWN).getType().isAir())b.getRelative(BlockFace.DOWN).setType(Material.STONE,false);var event=new PlayerInteractEvent(p,Action.RIGHT_CLICK_BLOCK,p.getInventory().getItemInMainHand(),b.getRelative(BlockFace.DOWN),BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);check(event.useItemInHand()==Event.Result.DENY,"native item-use path is consumed by custom placement");}
    private void negatives()throws Exception{
        site=floor(-232,-232);give(owner);site.setType(Material.DIRT,false);trophies.place(owner,site,EquipmentSlot.HAND);check(owner.getInventory().getItemInMainHand().isSimilar(skull)&&site.getType()==Material.DIRT,"occupied cell rejected without consuming or replacing block");site.setType(Material.AIR,false);
        var cow=world.spawn(site.getLocation().add(.5,0,.5),Cow.class,c->{c.setAI(false);c.setGravity(false);});trophies.place(owner,site,EquipmentSlot.HAND);check(count(owner)==1&&site.getType().isAir(),"living entity collision rejected without trapping it");cow.remove();
        cancelPlace=true;int beforeAttempt=attempts;click(owner,site);await(()->attempts>beforeAttempt&&trophies.journal().entries().isEmpty(),()->{cancelPlace=false;check(count(owner)==1&&site.getType().isAir(),"late cancelled native placement returns exact item");
            var safe=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));var c=safe.getChunkAt(0,0);ticket(c);var b=safe.getBlockAt(10,200,10);b.setType(Material.AIR,false);b.getRelative(BlockFace.DOWN).setType(Material.STONE,false);
            var visitor=actor(new Location(safe,8.5,200,10.5),UUID.randomUUID(),"SkullSafe",false);give(visitor);trophies.place(visitor,b,EquipmentSlot.HAND);check(count(visitor)==1&&b.getType().isAir(),"Batuu safezone rejects ordinary player placement");
            give(owner);placeEvents=0;click(owner,site);click(owner,site);await(()->placedEntry()!=null&&placedEntry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER&&displays(site)==1, this::placedChecks);
        });
    }
    private Cell cell(Block b){return new Cell(b.getWorld().getKey().toString(),b.getX(),b.getY(),b.getZ());}
    private Entry placedEntry(){return trophies.journal().at(cell(site));}
    private int count(Player p){return Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).filter(trophies::skull).mapToInt(ItemStack::getAmount).sum();}
    private long displays(Block b){return b.getWorld().getNearbyEntities(b.getLocation().add(.5,.5,.5),1,1,1).stream().filter(e->e instanceof ItemDisplay&&e.getPersistentDataContainer().has(new NamespacedKey("mandomc","rancor_trophy_display_v1"))).count();}
    private void placedChecks()throws Exception{
        check(site.getType()==Material.BARRIER&&count(owner)==0,"one item becomes one collision anchor");check(placeEvents==1,"one accepted block-place event per trophy");check(displays(site)==1,"exactly one visual display");check(placedEntry().item().equals(Base64.getEncoder().encodeToString(skull.serializeAsBytes())),"custody preserves full custom name/PDC/stack bytes");
        var supportBreak=new BlockBreakEvent(site.getRelative(BlockFace.DOWN),owner);Bukkit.getPluginManager().callEvent(supportBreak);check(!supportBreak.isCancelled(),"ordinary support break remains allowed");site.getRelative(BlockFace.DOWN).setType(Material.AIR,false);
        var blocks=new ArrayList<>(List.of(site,site.getRelative(BlockFace.DOWN)));var explosion=new EntityExplodeEvent(owner,site.getLocation(),blocks,1,ExplosionResult.DESTROY);trophies.entityExplosion(explosion);check(!blocks.contains(site)&&blocks.contains(site.getRelative(BlockFace.DOWN)),"trophy explosion filter protects only trophy and leaves neighbor policy unchanged");Bukkit.getPluginManager().callEvent(explosion);
        var piston=new BlockPistonExtendEvent(site.getRelative(BlockFace.EAST),List.of(site.getRelative(BlockFace.DOWN)),BlockFace.NORTH);trophies.piston(piston);check(!piston.isCancelled(),"trophy handler does not block neighbor piston movement");
        var clan=clans.state().create("SkullTest",owner.getUniqueId(),System.currentTimeMillis());var chunk=new ClanState.Chunk(world.getKey().toString(),site.getX()>>4,site.getZ()>>4);clans.state().claim(clan,chunk,4,c->true);clans.state().join(clan,recruit.getUniqueId(),4,System.currentTimeMillis());
        check(clans.state().role(clan,recruit.getUniqueId())==ClanRole.RECRUIT,"new member is actual native Recruit");
        trophies.breakTrophy(intruder,placedEntry());later(5,()->{check(site.getType()==Material.BARRIER&&count(intruder)==0,"foreign clan member cannot recover trophy");
            trophies.breakTrophy(recruit,placedEntry());await(()->trophies.journal().at(cell(site))==null&&count(recruit)==1,()->{
                check(site.getType().isAir()&&displays(site)==0,"legitimate break removes anchor and visual");check(recruit.getInventory().getItemInMainHand().isSimilar(skull)||Arrays.stream(recruit.getInventory().getStorageContents()).anyMatch(s->s!=null&&s.isSimilar(skull)),"Recruit build access returns exact non-storage trophy metadata");
                trophies.recover(recruit);later(5,()->{check(count(recruit)==1,"repeated recovery cannot duplicate return");claimsPlace(clan);});
            });
        });
    }
    private void claimsPlace(ClanState.Clan clan)throws Exception{
        give(intruder);int beforeAttempt=attempts;click(intruder,site);await(()->attempts>beforeAttempt&&trophies.journal().entries().isEmpty(),()->{
            check(count(intruder)==1&&site.getType().isAir(),"foreign claim placement is denied without item loss");give(recruit);click(recruit,site);await(()->placedEntry()!=null&&placedEntry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER&&displays(site)==1,()->{
                check(count(recruit)==0&&site.getType()==Material.BARRIER,"Recruit can place a trophy where building is permitted");
                for(int i=0;i<36;i++)recruit.getInventory().setItem(i,new ItemStack(Material.STONE,64));trophies.breakTrophy(recruit,placedEntry());await(()->placedEntry()==null&&site.getType().isAir()&&displays(site)==0,()->{
                    check(count(recruit)==0&&trophies.journal().entries().values().stream().anyMatch(e->e.phase()==Phase.RETURNING),"full inventory retains durable return rather than dropping or losing it");recruit.getInventory().setItem(0,null);trophies.recover(recruit);await(()->count(recruit)==1&&trophies.journal().entries().isEmpty(),()->{give(owner);click(owner,site);await(()->placedEntry()!=null&&placedEntry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER&&displays(site)==1,this::prepareRestart);});
                });
            });
        });
    }
    private void prepareRestart()throws Exception{
        var file=new YamlConfiguration();file.set("checks",checks);file.set("placed",placedEntry().id().toString());file.set("skull",Base64.getEncoder().encodeToString(skull.serializeAsBytes()));file.set("clan",clans.member(owner).id.toString());
        // Pause recovery while constructing exact crash-boundary snapshots in this disposable runtime.
        var task=trophies.getClass().getDeclaredField("task");task.setAccessible(true);((org.bukkit.scheduler.BukkitTask)task.get(trophies)).cancel();
        seed(file,"untouched",false,false,false);seed(file,"consumed",true,false,false);seed(file,"delivered",false,true,false);seed(file,"full",false,false,true);
        owner.saveData();file.set("owner",owner.getUniqueId().toString());getDataFolder().mkdirs();file.save(state());clans.save().get(5,java.util.concurrent.TimeUnit.SECONDS);
        getLogger().info("RANCOR TROPHY PHASE ONE PASSED: "+checks+" assertions; exact persisted custody fixtures saved");Bukkit.shutdown();
    }
    private void seed(YamlConfiguration file,String name,boolean consumed,boolean delivered,boolean full)throws Exception{
        var p=actor(new Location(world,-219.5,200,-219.5),UUID.randomUUID(),"Skull"+name,false);p.getInventory().clear();if(full)for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.STONE,64));else if(!consumed)p.getInventory().setItem(0,skull.clone());
        var e=new Entry(UUID.randomUUID(),p.getUniqueId(),new Cell(world.getKey().toString(),-212+actors.size(),200,-212),0,Base64.getEncoder().encodeToString(skull.serializeAsBytes()),Phase.PREPARED);
        if(consumed)p.getPersistentDataContainer().set(receiptKey,PersistentDataType.STRING,e.placeReceipt());if(delivered)p.getPersistentDataContainer().set(receiptKey,PersistentDataType.STRING,e.returnReceipt());p.saveData();online.remove(p.getUniqueId());worldPlayers.get(p.getWorld()).remove(handles.get(p.getUniqueId()));
        trophies.journal().reserve(e);if(delivered||full)trophies.journal().replace(e,e.returning(p.getUniqueId()));file.set("fixtures."+name+".owner",p.getUniqueId().toString());file.set("fixtures."+name+".record",e.id().toString());file.set("fixtures."+name+".name",p.getName());
    }
    private void restart()throws Exception{
        var file=YamlConfiguration.loadConfiguration(state());checks=file.getInt("checks");skull=ItemStack.deserializeBytes(Base64.getDecoder().decode(file.getString("skull")));site=world.getBlockAt(-232,200,-232);
        var entry=trophies.journal().entries().get(UUID.fromString(file.getString("placed")));check(entry!=null&&entry.phase()==Phase.PLACED&&site.getType()==Material.BARRIER,"placed world collision and journal survive actual restart");check(displays(site)==1,"saved visual is deduplicated across actual restart");check(decode(entry).isSimilar(skull),"placed item metadata survives actual restart");
        for(String name:List.of("untouched","consumed","delivered","full")){
            String path="fixtures."+name;var p=actor(new Location(world,-219.5,200,-219.5),UUID.fromString(file.getString(path+".owner")),file.getString(path+".name"),true);check((name.equals("consumed")||name.equals("full"))?count(p)==0:count(p)==1,"actual player NBT snapshot loaded: "+name);trophies.recover(p);
        }
        await(()->trophies.journal().entries().size()==2,()->{
            for(var p:actors)if(!p.getName().equals("Skullfull"))check(count(p)==1,"interrupted transfer returns exactly once: "+p.getName());
            var full=actors.stream().filter(p->p.getName().equals("Skullfull")).findFirst().orElseThrow();full.getInventory().setItem(0,null);trophies.recover(full);
            await(()->trophies.journal().entries().size()==1&&count(full)==1,()->{
                check(true,"full saved return delivers once after inventory space is made");owner=actor(new Location(world,-233.5,200,-231.5),UUID.fromString(file.getString("owner")),"SkullOwner",true);trophies.breakTrophy(owner,entry);
                await(()->trophies.journal().entries().isEmpty(),()->{check(count(owner)==1&&site.getType().isAir()&&displays(site)==0,"restart trophy legitimately recovers once with exact metadata");for(var p:actors)trophies.recover(p);later(5,()->{check(actors.stream().allMatch(p->count(p)==1),"repeat recovery after restart has no duplicates");finalWorldChecks();});});
            });
        });
    }
    private void inputOnly()throws Exception{
        var file=YamlConfiguration.loadConfiguration(state());skull=ItemStack.deserializeBytes(Base64.getDecoder().decode(file.getString("skull")));
        owner=actor(new Location(world,-233.5,200,-231.5),UUID.fromString(file.getString("owner")),"SkullOwner",true);site=floor(-232,-232);give(owner);
        var handle=handles.get(owner.getUniqueId());var mode=handle.getClass().getField("gameMode").get(handle);
        var posType=Class.forName("net.minecraft.core.BlockPos");var dirType=Class.forName("net.minecraft.core.Direction");var handType=Class.forName("net.minecraft.world.InteractionHand");var vecType=Class.forName("net.minecraft.world.phys.Vec3");var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");
        var support=site.getRelative(BlockFace.DOWN);var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(support.getX(),support.getY(),support.getZ());
        var vec=vecType.getConstructor(double.class,double.class,double.class).newInstance(site.getX()+.5,(double)site.getY(),site.getZ()+.5);var direction=dirType.getField("UP").get(null);var hand=handType.getField("MAIN_HAND").get(null);
        var hit=hitType.getConstructor(vecType,dirType,posType,boolean.class).newInstance(vec,direction,pos,false);var item=handle.getClass().getMethod("getItemInHand",handType).invoke(handle,hand);var level=world.getClass().getMethod("getHandle").invoke(world);
        var use=Arrays.stream(mode.getClass().getMethods()).filter(m->m.getName().equals("useItemOn")&&m.getParameterCount()==5).findFirst().orElseThrow();use.invoke(mode,handle,level,item,hand,hit);
        await(()->placedEntry()!=null&&placedEntry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER,()->{
            check(count(owner)==0,"actual native use-item-on consumes exactly one trophy");check(displays(site)==1,"actual native item-use creates exactly one visible trophy");
            var actionType=Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");var action=actionType.getField("START_DESTROY_BLOCK").get(null);var blockPos=posType.getConstructor(int.class,int.class,int.class).newInstance(site.getX(),site.getY(),site.getZ());
            mode.getClass().getMethod("handleBlockBreakAction",posType,actionType,dirType,int.class,int.class).invoke(mode,blockPos,action,direction,world.getMaxHeight(),1);
            await(()->trophies.journal().entries().isEmpty()&&count(owner)==1,()->{check(site.getType().isAir()&&displays(site)==0,"native survival destroy-block start legitimately recovers barrier-backed trophy");check(owner.getInventory().getItemInMainHand().isSimilar(skull),"native input round trip preserves exact named item");getLogger().info("RANCOR TROPHY INPUT PROBE PASSED: "+checks+" assertions");Bukkit.shutdown();});
        });
    }
    private final List<Entry> faceEntries=new ArrayList<>();
    private final BlockFace[] faceCases={BlockFace.UP,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.DOWN,BlockFace.EAST,BlockFace.UP};
    private java.io.File faceState(){return new java.io.File(getDataFolder(),"faces.yml");}
    private void facesBegin()throws Exception{
        var old=YamlConfiguration.loadConfiguration(state());owner=actor(new Location(world,-233.5,200,-231.5),UUID.fromString(old.getString("owner")),"SkullOwner",true);owner.getInventory().clear();skull=mando.items().create(RancorTrophies.ITEM);
        var meta=skull.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Every Face Trophy"));meta.getPersistentDataContainer().set(new NamespacedKey("trophyprobe","memory"),PersistentDataType.STRING,"six faces exact");meta.setMaxStackSize(1);skull.setItemMeta(meta);
        if(faceState().exists()){
            var saved=YamlConfiguration.loadConfiguration(faceState());skull=ItemStack.deserializeBytes(Base64.getDecoder().decode(saved.getString("skull")));
            for(String id:saved.getStringList("entries")){var entry=trophies.journal().entries().get(UUID.fromString(id));check(entry!=null&&entry.phase()==Phase.PLACED,"face journal survives actual restart");faceEntries.add(entry);inspectPose(entry);}
            recoverFace(0);
        }else{
            var json=net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(skull.getItemMeta().lore().getFirst());check(json.contains("mandomc:trophies")&&(json.contains("\\ue000")||json.contains("\ue000")),"native Legendary lore includes isolated TROPHY bitmap font");
            if(new java.io.File(getDataFolder(),"resume-partial.txt").exists()){
                for(int i=0;i<4;i++){var e=trophies.journal().at(new Cell(world.getKey().toString(),-232+(i%2)*3,200,-232+(i/2)*3));check(e!=null,"prior passed face retained");faceEntries.add(e);inspectPose(e);}placeFace(4);
            }else placeFace(0);
        }
    }
    private void placeFace(int index)throws Exception{
        if(index==faceCases.length){
            var saved=new YamlConfiguration();saved.set("entries",faceEntries.stream().map(e->e.id().toString()).toList());saved.set("skull",Base64.getEncoder().encodeToString(skull.serializeAsBytes()));saved.set("checks",checks);saved.save(faceState());owner.saveData();getLogger().info("RANCOR FACE INPUT PASSED: "+checks+" assertions; eight orientations/partial/replaceable cases saved");Bukkit.shutdown();return;
        }
        var face=faceCases[index];site=world.getBlockAt(-232+(index%2)*3,200,-232+(index/2)*3);site.setType(Material.AIR,false);site.getRelative(BlockFace.DOWN).setType(Material.AIR,false);var clicked=site.getRelative(face.getOppositeFace());clicked.setType(index==0||index==6?Material.STONE_SLAB:Material.STONE,false);
        if(index==7){clicked=site;clicked.setType(Material.SHORT_GRASS,false);}var against=clicked;
        moveActor(site.getLocation().add(-1.5,0,.5));give(owner);nativeUse(against,face);
        await(()->placedEntry()!=null&&placedEntry().phase()==Phase.PLACED&&site.getType()==Material.BARRIER&&displays(site)==1,()->{
            var entry=placedEntry();check(entry.face().name().equals(face.name()),"actual native input records clicked face "+index+"/"+face);check(count(owner)==0,"face placement consumes exactly one item");inspectPose(entry);faceEntries.add(entry);
            if(against!=site){var event=new BlockBreakEvent(against,owner);Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled(),"clicked support can be legitimately removed "+face);against.setType(Material.AIR,false);}
            later(25,()->{check(trophies.journal().at(cell(site))!=null&&site.getType()==Material.BARRIER&&displays(site)==1,"unsupported trophy remains placed "+face);placeFace(index+1);});
        });
    }
    private ItemDisplay visual(Entry entry){var b=world.getBlockAt(entry.cell().x(),entry.cell().y(),entry.cell().z());return b.getWorld().getNearbyEntities(b.getLocation().add(.5,.5,.5),1,1,1).stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).filter(d->entry.id().toString().equals(d.getPersistentDataContainer().get(new NamespacedKey("mandomc","rancor_trophy_display_v1"),PersistentDataType.STRING))).findFirst().orElseThrow();}
    private void inspectPose(Entry entry){
        var display=visual(entry);check(displays(world.getBlockAt(entry.cell().x(),entry.cell().y(),entry.cell().z()))==1,"one display per oriented trophy");check(decode(entry).isSimilar(skull),"orientation keeps exact named stack metadata");check(display.getTransformation().getTranslation().distance(new org.joml.Vector3f(0,.0625f,0))<1e-6,"head centered before rotation");
        var expected=entry.face()==Face.UP?new org.bukkit.util.Vector(0,0,-1):BlockFace.valueOf(entry.face().name()).getDirection();check(display.getLocation().getDirection().distance(expected)<1e-5,"rendered forward faces outward "+entry.face());
        var center=new org.bukkit.util.Vector(entry.cell().x()+.5,entry.cell().y()+.5,entry.cell().z()+.5);if(entry.face()==Face.UP)center.add(new org.bukkit.util.Vector(0,-.0625,0));else center.subtract(BlockFace.valueOf(entry.face().name()).getDirection().multiply(.0625));check(display.getLocation().toVector().distance(center)<1e-5,"oriented geometry remains inside reserved cell and touches clicked side");
    }
    private void recoverFace(int index)throws Exception{
        if(index==faceEntries.size()){trophies.recover(owner);later(25,()->{check(count(owner)==faceCases.length&&trophies.journal().entries().isEmpty(),"all face returns exactly once after restart");getLogger().info("RANCOR FACE RESTART PASSED: "+checks+" assertions");Bukkit.shutdown();});return;}
        var e=faceEntries.get(index);site=world.getBlockAt(e.cell().x(),e.cell().y(),e.cell().z());moveActor(site.getLocation().add(-1.5,0,.5));nativeBreak(site);await(()->!trophies.journal().entries().containsKey(e.id())&&count(owner)==index+1,()->{check(site.getType().isAir()&&displays(site)==0,"native break recovers oriented trophy without leftovers");check(Arrays.stream(owner.getInventory().getStorageContents()).filter(Objects::nonNull).filter(trophies::skull).allMatch(item->item.isSimilar(skull)),"every face returns exact item bytes");recoverFace(index+1);});
    }
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
    private ItemStack decode(Entry e){return ItemStack.deserializeBytes(Base64.getDecoder().decode(e.item()));}
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable failure){fail(failure);}},ticks);}
    private void await(BooleanSupplier predicate,Check action){await(predicate,action,0);}
    private void await(BooleanSupplier predicate,Check action,int tries){later(2,()->{if(predicate.getAsBoolean())action.run();else if(tries>150)throw new AssertionError("Timed out waiting for native custody transition");else await(predicate,action,tries+1);});}
    private void check(boolean condition,String why){if(!condition)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
    private void fail(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"RANCOR TROPHY PROBE FAILED",failure);Bukkit.shutdown();}
    @SuppressWarnings("unchecked") private Player actor(Location where,UUID id,String name,boolean load)throws Exception{
        var p=nativeTestPlayer(where.getWorld(),where,id,name);actors.add(p);if(load)p.loadData();p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(id,h);var level=p.getWorld().getClass().getMethod("getHandle").invoke(p.getWorld());var list=worldPlayers.computeIfAbsent(p.getWorld(),w->{try{return(List<Object>)level.getClass().getMethod("players").invoke(level);}catch(Exception error){throw new RuntimeException(error);}});list.add(h);online.put(id,h);return p;
    }
    @Override public void onDisable(){HandlerList.unregisterAll((Listener)this);entities.forEach(e->{if(e.isValid())e.remove();});for(var p:actors){online.remove(p.getUniqueId());var list=worldPlayers.get(p.getWorld());if(list!=null)list.remove(handles.get(p.getUniqueId()));}chunks.forEach(c->c.removePluginChunkTicket(this));}
    private Player nativeTestPlayer(World world, Location location, UUID id, String name) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(id, name);
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
