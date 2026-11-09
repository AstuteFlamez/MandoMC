package net.mandomc.gameplay.dungeon;

import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.MiniMessage;import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.events.ManagedEvent;import net.mandomc.gameplay.items.ItemCatalog;import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;import org.bukkit.block.Container;import org.bukkit.block.data.Directional;import org.bukkit.configuration.ConfigurationSection;import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.*;import org.bukkit.event.entity.*;import org.bukkit.event.player.PlayerInteractEvent;import org.bukkit.event.world.EntitiesLoadEvent;import org.bukkit.inventory.*;import org.bukkit.persistence.PersistentDataType;import org.bukkit.plugin.java.JavaPlugin;

/** One shared, scheduled palace run. Mythic owns mobs; this class owns room/cache/gate progression. */
public final class JabbaEvent implements ManagedEvent,Listener {
 private record Cache(JabbaSettings.Chest spec,List<ItemStack> loot){}
 private final boolean broadcastStart;private final JavaPlugin plugin;private final ItemCatalog items;private final JabbaSettings settings;private final ConfigurationSection messages;private final MythicMobAccess mythic;private final DungeonProgress progress;private final Path journal;private final NamespacedKey ownerKey;private final String tag;
 private final Set<String> reserved=new HashSet<>();private final Set<UUID> owned=new HashSet<>();private final Map<String,Cache> caches=new HashMap<>();private final List<Chunk> chunks=new ArrayList<>();private World world;private NativeGates gates;private boolean running;private String run;private int spawnedAt;
 public JabbaEvent(JavaPlugin plugin,ItemCatalog items,ConfigurationSection config) throws Exception {
  if(!"jabba".equals(config.getString("id")))throw new IllegalArgumentException("jabba: invalid event id");
  this.plugin=plugin;broadcastStart=config.getBoolean("broadcast-start",true);this.items=items;settings=JabbaSettings.read(config);progress=new DungeonProgress(settings.rooms().size());messages=config.getConfigurationSection("messages");
  for(String s:List.of("start","caches","opened","complete","stopped","looted"))if(messages==null||!messages.isString(s))throw new IllegalArgumentException("jabba: missing message "+s);
  mythic=new MythicMobAccess();journal=plugin.getDataFolder().toPath().resolve("jabba/"+settings.profile()+"-run.yml");ownerKey=new NamespacedKey(plugin,"jabba_run");tag="mandomc_jabba_"+settings.profile();
  recover();plugin.getServer().getPluginManager().registerEvents(this,plugin);
 }
 public String id(){return "jabba";}public String displayName(){return "Jabba's Palace";}public boolean running(){return running;}public DungeonProgress progress(){return progress;}
 public boolean relevant(Player player){return settings.contains(player.getLocation());}
 public JabbaSettings settings(){return settings;}public int cachesRemaining(){return caches.size();}
 private net.kyori.adventure.text.Component text(String key,int room,int count){return MiniMessage.miniMessage().deserialize(messages.getString(key),Placeholder.unparsed("room",""+room),Placeholder.unparsed("count",""+count));}
 private void nearby(String key){var text=text(key,progress.room(),caches.size());for(var p:Bukkit.getOnlinePlayers())if(settings.contains(p.getLocation()))p.sendMessage(text);}
 private void ticket(){for(int x=settings.min().x()>>4;x<=settings.max().x()>>4;x++)for(int z=settings.min().z()>>4;z<=settings.max().z()>>4;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(plugin);chunks.add(c);}}
 private List<JabbaSettings.Chest> allChests(){return settings.rooms().stream().flatMap(r->r.chests().stream()).toList();}
 private String key(Location l){return l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ();}
 public void start(){
  if(running)throw new IllegalStateException("Jabba already running");world=Bukkit.getWorld(NamespacedKey.fromString(settings.world()));if(world==null)throw new IllegalStateException("Tatooine not loaded");
  try {
   if(Files.isRegularFile(journal))recover();
   for(String id:settings.loot().keySet())if(!items.ids().contains(id)||items.create(id)==null)throw new IllegalStateException("Unknown Jabba loot "+id);
   ticket();gates=new NativeGates(plugin,world,settings,plugin.getDataFolder().toPath().resolve(settings.blueprints()).toFile());
   for(var c:allChests()){var b=c.pos().location(world).getBlock();if(!b.getType().isAir()&&b.getType()!=Material.CHEST)throw new IllegalStateException("Occupied stash coordinate "+c.pos().key());if(b.getState() instanceof Container container&&!container.getInventory().isEmpty())throw new IllegalStateException("Nonempty legacy chest at "+c.pos().key());}
   run=UUID.randomUUID().toString();saveJournal();running=true;progress.start();for(var c:allChests())c.pos().location(world).getBlock().setType(Material.AIR,false);gates.reset();spawnCaches();nearby("caches");
   if(broadcastStart)Bukkit.getServer().broadcast(text("start",1,0));openRoom();
  }catch(Exception e){stop(false);throw new IllegalStateException("Cannot start Jabba: "+e.getMessage(),e);}
 }
 private void openRoom(){var room=settings.rooms().get(progress.room()-1);nearby("opened");gates.open(room.gate(),room.movement(),()->{if(!running)return;try{spawnRoom();}catch(RuntimeException e){fail(e);}});}
 private void spawnRoom(){var room=settings.rooms().get(progress.room()-1);var ids=new ArrayList<UUID>();var random=ThreadLocalRandom.current();var guardTypes=List.of("GamorreanGuard","QuarrenGuard","TwilekGuard");
  for(var p:room.spawns())for(int i=0;i<room.count();i++){String type=room.mob().equals("guards")?guardTypes.get(random.nextInt(3)):room.mob();var entity=mythic.spawn(type,p.location(world).add(0.5,0,0.5),room.mob().equals("guards")?progress.room():1);owned.add(entity.getUniqueId());ids.add(entity.getUniqueId());entity.addScoreboardTag(tag);entity.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,run);entity.setPersistent(true);if(entity instanceof Mob mob)mob.setRemoveWhenFarAway(false);mythic.persist(entity);}
  progress.spawned(ids);spawnedAt=Bukkit.getCurrentTick();saveJournal();
 }
 @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){if(!running)return;if(progress.died(e.getEntity().getUniqueId()))try{if(progress.phase()==DungeonProgress.Phase.OPENING)openRoom();else nearby("complete");}catch(RuntimeException error){fail(error);}}
 private List<ItemStack> roll(){var random=ThreadLocalRandom.current();double total=settings.loot().values().stream().mapToDouble(Double::doubleValue).sum();var out=new ArrayList<ItemStack>();for(int i=0,n=random.nextInt(1,4);i<n;i++){double r=random.nextDouble(total);for(var entry:settings.loot().entrySet()){r-=entry.getValue();if(r<0){out.add(Objects.requireNonNull(items.create(entry.getKey())));break;}}}return List.copyOf(out);}
 private void spawnCaches(){var prepared=new LinkedHashMap<String,Cache>();for(var spec:allChests())prepared.put(spec.pos().key(),new Cache(spec,roll()));
  for(var cache:prepared.values()){var b=cache.spec.pos().location(world).getBlock();if(!b.getType().isAir())throw new IllegalStateException("Stash position was obstructed: "+cache.spec.pos().key());b.setType(Material.CHEST,false);var data=(Directional)b.getBlockData();data.setFacing(cache.spec.face());b.setBlockData(data,false);}
  caches.putAll(prepared);
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(PlayerInteractEvent e){if(!running||e.getClickedBlock()==null||e.getClickedBlock().getWorld()!=world)return;String key=key(e.getClickedBlock().getLocation());var cache=caches.get(key);if(cache==null)return;e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
  if(e.getPlayer().getGameMode()==GameMode.SPECTATOR)return;caches.remove(key);e.getClickedBlock().setType(Material.AIR,false);for(var item:cache.loot){var left=e.getPlayer().getInventory().addItem(item.clone());left.values().forEach(stack->world.dropItemNaturally(e.getPlayer().getLocation(),stack));}
  var loc=cache.spec.pos().location(world).add(0.5,0.7,0.5);world.spawnParticle(Particle.TOTEM_OF_UNDYING,loc,30,0.3,0.3,0.3,0.05);world.playSound(loc,Sound.ENTITY_PLAYER_LEVELUP,1,1.2f);e.getPlayer().sendMessage(text("looted",progress.room(),caches.size()));
 }
 private boolean reserved(Location loc){return loc.getWorld().getKey().toString().equals(settings.world())&&(reserved.contains(key(loc))||allChests().stream().anyMatch(c->c.pos().key().equals(key(loc))));}
 @EventHandler(ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(reserved(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(ignoreCancelled=true)public void place(BlockPlaceEvent e){if(reserved(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler(ignoreCancelled=true)public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->reserved(b.getLocation()));}
 @EventHandler(ignoreCancelled=true)public void explode(BlockExplodeEvent e){e.blockList().removeIf(b->reserved(b.getLocation()));}
 @EventHandler(ignoreCancelled=true)public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getLocation())||reserved(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
 @EventHandler(ignoreCancelled=true)public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->reserved(b.getLocation())||reserved(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);}
 @EventHandler(ignoreCancelled=true)public void change(EntityChangeBlockEvent e){if(reserved(e.getBlock().getLocation()))e.setCancelled(true);}
 @EventHandler public void loaded(EntitiesLoadEvent e){for(var entity:e.getEntities())if(entity.getScoreboardTags().contains(tag)&&(!running||!Objects.equals(run,entity.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING))))mythic.remove(entity);}
 public void tick(){if(!running||progress.phase()!=DungeonProgress.Phase.COMBAT||Bukkit.getCurrentTick()-spawnedAt<40)return;for(var id:progress.mobs()){var entity=Bukkit.getEntity(id);if(entity==null||!entity.isValid()){fail(new IllegalStateException("Dungeon mob disappeared without a kill; operation reset"));break;}}}
 private void fail(RuntimeException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Jabba operation reset after error",e);stop(true);}
 private void saveJournal(){try{var c=new YamlConfiguration();c.set("world",settings.world());var plan=new ArrayList<>(gates.resetPlan());allChests().forEach(chest->plan.add(chest.pos().key()+"|minecraft:air"));reserved.clear();plan.forEach(row->reserved.add(row.split("\\|",2)[0]));c.set("cleanup",plan);c.set("entities",owned.stream().map(UUID::toString).toList());Files.createDirectories(journal.getParent());var tmp=journal.resolveSibling(journal.getFileName()+".tmp");c.save(tmp.toFile());Files.move(tmp,journal,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception e){throw new IllegalStateException("Cannot checkpoint dungeon cleanup",e);}}
 private void recover() throws Exception {if(!Files.isRegularFile(journal))return;var c=new YamlConfiguration();c.load(journal.toFile());var w=Bukkit.getWorld(NamespacedKey.fromString(c.getString("world","")));if(w==null)throw new IllegalStateException("Cannot recover dungeon without its world");var plan=c.getStringList("cleanup");if(plan.size()>10000)throw new IllegalStateException("Oversized recovery plan");for(String row:plan){var a=row.split("\\|",2);reserved.add(a[0]);var pos=JabbaSettings.Pos.parse(a[0]);pos.location(w).getBlock().setBlockData(Bukkit.createBlockData(a[1]),false);}for(String id:c.getStringList("entities")){var entity=Bukkit.getEntity(UUID.fromString(id));if(entity!=null)mythic.remove(entity);}w.getEntities().stream().filter(e->e.getScoreboardTags().contains(tag)||e.getScoreboardTags().contains("mandomc_jabba_gate_"+settings.profile())).toList().forEach(mythic::remove);plugin.getLogger().info("Recovered interrupted Jabba operation: gates closed and caches/mobs cleared.");}
 public void stop(boolean announce){boolean wasRunning=running;running=false;try{
   if(wasRunning){if(announce)nearby("stopped");
    // Forced operation cleanup moves occupants outside before restoring solid gate panels.
    for(var p:Bukkit.getOnlinePlayers())if(settings.contains(p.getLocation()))try{var exit=((net.mandomc.bootstrap.MandoPlugin)plugin).worlds().spawn(settings.world());if(plugin instanceof net.mandomc.bootstrap.MandoPlugin m)m.combat().clear(p);p.teleport(exit);}catch(RuntimeException error){plugin.getLogger().warning("Could not extract player before dungeon reset: "+error.getMessage());}
    for(UUID id:owned){var e=Bukkit.getEntity(id);if(e!=null)mythic.remove(e);}for(var c:allChests())c.pos().location(world).getBlock().setType(Material.AIR,false);if(gates!=null)gates.reset();
   }
  }catch(Exception e){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Jabba cleanup checkpoint retained for recovery",e);}finally{if(gates!=null){gates.close();gates=null;}chunks.forEach(c->c.removePluginChunkTicket(plugin));chunks.clear();owned.clear();caches.clear();progress.reset();}}
 public void close(){stop(false);HandlerList.unregisterAll(this);}
}
