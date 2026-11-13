package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.events.beskar.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class BeskarProbe extends JavaPlugin implements Listener {
 private MandoPlugin mando;private BeskarEvent event;private World world;private Player player;private int checks;
 private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();private Map<UUID,Object> online;
 private final List<Item> loot=new ArrayList<>();private final List<Integer> amounts=new ArrayList<>();private final Map<MineSurvey.Pos,String> originals=new LinkedHashMap<>();
 private MineSurvey.Pos cancel;private boolean stopping;
 private interface Step{void run()throws Exception;}
 private void later(long ticks,Step s){Bukkit.getScheduler().runTaskLater(this,()->{try{s.run();}catch(Throwable t){fail(t);}},ticks);}
 private void check(boolean ok,String m){if(!ok)throw new IllegalStateException(m);checks++;}
 public void onEnable(){getServer().getPluginManager().registerEvents(this,this);later(100,this::begin);}
 @EventHandler(priority=EventPriority.HIGHEST) public void cancel(BlockBreakEvent e){if(cancel!=null&&e.getBlock().equals(cancel.block(world)))e.setCancelled(true);}
 @EventHandler public void loot(ItemSpawnEvent e){if(mando!=null&&"beskar_scrap".equals(mando.items().identify(e.getEntity().getItemStack()))){loot.add(e.getEntity());amounts.add(e.getEntity().getItemStack().getAmount());}}
 @SuppressWarnings("unchecked") private void begin()throws Exception{
  check(Bukkit.getPort()==25588,"Disposable port only");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled(),"Main plugin enabled");event=(BeskarEvent)mando.events().event("beskar");check(event!=null,"Beskar registered in sole scheduler");world=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));
  var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var f=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");f.setAccessible(true);online=(Map<UUID,Object>)f.get(list);
  for(var h:event.hosts())h.stand().block(world).getChunk().addPluginChunkTicket(this);
  player=actor(new Location(world,0.5,8,-44.5));check(event.hosts().size()==25,"Final25 node target");
  if(Files.exists(getDataFolder().toPath().resolve("restart.flag"))){later(80,this::recovered);return;}
  selectNativePlan();
 }
 private void testStart()throws Exception {
  for(var h:event.hosts()){originals.put(h.ore(),h.ore().block(world).getBlockData().getAsString());check(h.ore().block(world).getType()==Material.DEEPSLATE,"Original deepslate host");}
  Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("originals.txt"),String.join("\n",originals.entrySet().stream().map(e->e.getKey().x()+","+e.getKey().y()+","+e.getKey().z()+"|"+e.getValue()).toList()));
  mando.events().stop(false);check(mando.events().start("beskar"),"Start through existing event manager");awaitActive(0,this::mining);
 }
 private void selectNativePlan()throws Exception {
  var floors=new ArrayList<MineSurvey.Pos>();
  for(String line:Files.readAllLines(getDataFolder().toPath().resolve("all-floors.txt"))){var a=Arrays.stream(line.split(",")).mapToInt(Integer::parseInt).toArray();floors.add(new MineSurvey.Pos(a[0],a[1],a[2]));}
  var scan=new MineSurvey(world,floors,event.entrance());
  new org.bukkit.scheduler.BukkitRunnable(){public void run(){try{if(!scan.step(400))return;cancel();
   var candidates=new ArrayList<BeskarEvent.Host>();
   for(String line:Files.readAllLines(getDataFolder().toPath().resolve("candidate-hosts.txt"))){
    var a=Arrays.stream(line.split(",")).mapToInt(Integer::parseInt).toArray();var ore=new MineSurvey.Pos(a[0],a[1],a[2]);var stand=new MineSurvey.Pos(a[3],a[4],a[5]);
    if(!scan.reached(stand))continue;var b=ore.block(world);var eye=new Location(world,stand.x()+.5,scan.feet(stand)+1.62,stand.z()+.5);var aim=b.getLocation().add(.5+(stand.x()-b.getX())*.499,.5,.5+(stand.z()-b.getZ())*.499).subtract(eye).toVector();
    var hit=world.rayTraceBlocks(eye,aim.clone().normalize(),Math.min(4.5,aim.length()+.05),FluidCollisionMode.NEVER,true);
    if(hit!=null&&b.equals(hit.getHitBlock()))candidates.add(new BeskarEvent.Host(ore,stand));
   }
   check(candidates.size()>=25,"Enough actual body-sized reachable deepslate hosts: "+candidates.size());var selected=new ArrayList<BeskarEvent.Host>();selected.add(candidates.removeFirst());
   while(selected.size()<25){var next=candidates.stream().max(java.util.Comparator.comparingDouble(h->selected.stream().mapToDouble(other->{var a=h.ore();var b=other.ore();return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}).min().orElse(0))).orElseThrow();selected.add(next);candidates.remove(next);}
   Set<MineSurvey.Pos> paths=new LinkedHashSet<>();for(var h:selected)paths.addAll(scan.route(h.stand()));
   var config=new org.bukkit.configuration.file.YamlConfiguration();var file=new java.io.File(mando.getDataFolder(),"events/beskar.yml");config.load(file);config.set("walkway",paths.stream().map(MineSurvey.Pos::list).toList());config.set("nodes",selected.stream().map(h->List.of(h.ore().x(),h.ore().y(),h.ore().z(),h.stand().x(),h.stand().y(),h.stand().z())).toList());config.save(file);
   config.save(getDataFolder().toPath().resolve("native-plan.yml").toFile());getLogger().info("Native mine plan: "+scan.reachedCount()+" connected body-sized tiles; "+candidates.size()+" eligible hosts; reduced witness "+paths.size());
   mando.events().reload();event=(BeskarEvent)mando.events().event("beskar");testStart();
  }catch(Throwable t){cancel();fail(t);}}}.runTaskTimer(BeskarProbe.this,1,1);
 }
 private void awaitActive(int tries,Step next)throws Exception {check(tries<100,"Bounded preparation completes");if(event.miningActive()){next.run();return;}check(event.running(),"Preparation still owned by event manager");later(2,()->awaitActive(tries+1,next));}
 private void counter(String expected)throws Exception{mando.events().open(player);var stack=player.getOpenInventory().getTopInventory().getItem(11);check(stack!=null,"Actual menu current item");String lore=String.join("\n",stack.getItemMeta().lore().stream().map(PlainTextComponentSerializer.plainText()::serialize).toList());check(lore.contains(expected),"Actual /events lore: "+lore+" expected "+expected);}
 private void mine(int index,Material tool)throws Exception{
  var h=event.hosts().get(index);player.teleport(h.stand().block(world).getLocation().add(.5,1,.5));player.setGameMode(GameMode.SURVIVAL);var pick=new ItemStack(tool);if(index%2==0)pick.addUnsafeEnchantment(Enchantment.FORTUNE,100);else pick.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);player.getInventory().setItemInMainHand(pick);
  check(player.breakBlock(h.ore().block(world)),"Native registered node breaks in warzone "+index);check(h.ore().block(world).getType().isAir(),"Native break removes node");
 }
 private void mining()throws Exception{
  check(event.remainingCount()==25,"25 initially active");for(var h:event.hosts())check(h.ore().block(world).getType()==Material.ANCIENT_DEBRIS,"Embedded Beskar presentation");counter("Beskar Ore Blocks Left: 25/25");
  var first=event.hosts().get(0);player.teleport(first.stand().block(world).getLocation().add(.5,1,.5));player.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_PICKAXE));check(!player.breakBlock(first.ore().block(world)),"Weak pick denied");check(event.remainingCount()==25,"Denied tool no count loss");
  player.setGameMode(GameMode.CREATIVE);player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));check(!player.breakBlock(first.ore().block(world)),"Creative cannot farm event");player.setGameMode(GameMode.SURVIVAL);
  cancel=first.ore();check(!player.breakBlock(first.ore().block(world)),"Late cancellation retained");cancel=null;check(event.remainingCount()==25,"Cancelled reservation restored");
  var notification=new BlockBreakEvent(first.ore().block(world),player);Bukkit.getPluginManager().callEvent(notification);
  check(!notification.isCancelled(),"Valid mining notification passes policy");later(2,()->{
   check(event.remainingCount()==25&&amounts.isEmpty(),"Notification without native removal consumes nothing");mineSeven();
  });
 }
 private void mineSeven()throws Exception {
  for(int i=0;i<7;i++)mine(i,Material.DIAMOND_PICKAXE);later(12,()->{
   check(event.remainingCount()==18,"Seven consumed nodes only");check(amounts.size()==7,"Exactly seven reward stacks; no native duplicate");counter("Beskar Ore Blocks Left: 18/25");
   for(int i=7;i<25;i++)mine(i,Material.NETHERITE_PICKAXE);later(12,this::depleted);
  });
 }
 private void depleted()throws Exception{
  check(event.remainingCount()==0,"Exactly25 consumed");counter("Beskar Ore Blocks Left: 0/25");check(amounts.size()==25,"Exactly25 rewards");check(amounts.stream().allMatch(n->n>=1&&n<=5),"Shared final1-5 quantity independent of enchantments");check(amounts.stream().mapToInt(i->i).sum()>=25&&amounts.stream().mapToInt(i->i).sum()<=125,"Bounded event supply");
  mando.events().stop(false);for(var e:originals.entrySet())check(e.getKey().block(world).getBlockData().getAsString().equals(e.getValue()),"Mined hosts restored exactly");check(!event.running()&&event.remainingCount()==0,"Inactive state clears counter");mando.events().queue("beskar");mando.events().open(player);var queued=player.getOpenInventory().getTopInventory().getItem(13);check(queued.getItemMeta().lore().stream().anyMatch(c->PlainTextComponentSerializer.plainText().serialize(c).contains("Inactive")),"Inactive queued entry no stale count");
  check(mando.events().start("beskar"),"Second generation starts");awaitActive(0,()->{for(int i=0;i<3;i++)mine(i,Material.DIAMOND_PICKAXE);later(5,()->{check(event.remainingCount()==22,"Crash fixture has22unmined");world.save();later(30,()->{Files.writeString(getDataFolder().toPath().resolve("CRASH_READY.txt"),"Snapshot active native region files now");awaitSnapshot(0);});});});
 }
 private void awaitSnapshot(int tries)throws Exception{check(tries<600,"Crash snapshot acknowledged");if(Files.exists(getDataFolder().toPath().resolve("snapshot.ack"))){getLogger().info("BESKAR STAGE A PASSED: "+checks+" assertions; active crash snapshot captured");Files.writeString(getDataFolder().toPath().resolve("STAGE_A.txt"),Integer.toString(checks));stopping=true;Bukkit.shutdown();return;}later(1,()->awaitSnapshot(tries+1));}
 private void recovered()throws Exception{
  check(!event.recovering()&&!event.running(),"Startup restores rather than resuming event");
  for(String line:Files.readAllLines(getDataFolder().toPath().resolve("originals.txt"))){var a=line.split(java.util.regex.Pattern.quote("|"),2);var p=Arrays.stream(a[0].split(",")).mapToInt(Integer::parseInt).toArray();check(world.getBlockAt(p[0],p[1],p[2]).getBlockData().equals(Bukkit.createBlockData(a[1])),"Crash restores exact oriented host "+line);}
  var host=event.hosts().get(0).ore();var original=host.block(world).getBlockData();host.block(world).setType(Material.AIR,false);mando.events().reload();event=(BeskarEvent)mando.events().event("beskar");later(20,()->{check(host.block(world).getType().isAir(),"Completed receipts preserve later legitimate air edit");host.block(world).setBlockData(original,false);String text="BESKAR NATIVE PASSED: "+checks+" restart assertions plus stageA;25nodes, real mining,1-5scrap, menu, cancellation, exact reset and crash recovery";Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),text);getLogger().info(text);stopping=true;Bukkit.shutdown();});
 }
 private void fail(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"BESKAR NATIVE FAILED",t);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),t.toString());}catch(Exception ignored){}stopping=true;Bukkit.shutdown();}
    @SuppressWarnings("unchecked") private Player actor(Location at)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BeskarCheck"+actors.size());
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());
        Player player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
        actors.add(player);handles.put(player.getUniqueId(),handle);online.put(player.getUniqueId(),handle);
        level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);
        player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
    @SuppressWarnings("unchecked") private void cleanup(){
        Bukkit.getScheduler().cancelTasks(this);for(var item:loot)item.remove();loot.clear();if(world!=null)world.removePluginChunkTickets(this);
        for(var actor:List.copyOf(actors))try {
            actor.closeInventory();if(online!=null)online.remove(actor.getUniqueId());Object handle=handles.get(actor.getUniqueId());
            for(var w:Bukkit.getWorlds()){Object level=w.getClass().getMethod("getHandle").invoke(w);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
        }catch(Exception error){getLogger().warning("Fixture cleanup: "+error);}
        actors.clear();handles.clear();
    }

 public void onDisable(){cleanup();}
}
