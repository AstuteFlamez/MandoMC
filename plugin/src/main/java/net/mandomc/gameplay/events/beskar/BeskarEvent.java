package net.mandomc.gameplay.events.beskar;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.items.OreDropQuantity;
import net.mandomc.gameplay.territory.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.mandomc.gameplay.events.beskar.MineSurvey.Pos;

/** One twenty-five temporary nodes, owned by the existing event scheduler, never a region reset. */
public final class BeskarEvent implements ManagedEvent, EventMining, Listener {
    public record Host(Pos ore,Pos stand) {}
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final ConfigurationSection config;
    private final World world;
    private final TerritoryService territory;
    private final List<Pos> walkway;
    private final List<Host> hosts;
    private final Pos entrance;
    private final int maximumSeconds;
    private final Path directory;
    private final NamespacedKey marker;
    private final ArrayDeque<Path> recovery=new ArrayDeque<>();
    private final Set<Chunk> tickets=new HashSet<>();
    private final Map<Pos,UUID> pending=new HashMap<>();
    private final Map<BlockBreakEvent,Pos> breaks=new IdentityHashMap<>();
    private BeskarJournal.State state;
    private BeskarJournal journal;
    private MineSurvey survey;
    private BukkitTask preparation,recoverer;
    private boolean active,preparing,closed,recoveryFailed;
    private long deadline;

    public BeskarEvent(JavaPlugin plugin,ItemCatalog items,ConfigurationSection config)throws Exception {
        this.plugin=plugin;this.items=items;this.config=config;
        if(!"beskar".equals(config.getString("id"))||config.getInt("node-count")!=25)throw new IllegalArgumentException("Beskar requires exactly25 nodes");
        world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString(config.getString("world",""))),"Beskar world not loaded");
        territory=Objects.requireNonNull(Bukkit.getServicesManager().load(TerritoryService.class),"Native territory authority required");
        maximumSeconds=config.getInt("maximum-seconds");if(maximumSeconds<1||maximumSeconds>3300)throw new IllegalArgumentException("Invalid Beskar duration");
        entrance=point(config.getIntegerList("entrance"),0);var floors=new ArrayList<Pos>();
        for(Object raw:Objects.requireNonNull(config.getList("walkway")))floors.add(point((List<?>)raw,0));
        if(floors.isEmpty()||floors.size()>20000||!floors.contains(entrance))throw new IllegalArgumentException("Invalid bounded mine route graph");
        walkway=List.copyOf(new LinkedHashSet<>(floors));var nodes=new ArrayList<Host>();
        for(Object raw:Objects.requireNonNull(config.getList("nodes"))){var list=(List<?>)raw;if(list.size()!=6)throw new IllegalArgumentException("Beskar node requires host and stand");nodes.add(new Host(point(list,0),point(list,3)));}
        hosts=List.copyOf(nodes);
        if(hosts.size()!=25||hosts.stream().map(Host::ore).distinct().count()!=25||hosts.stream().anyMatch(h->!walkway.contains(h.stand)||walkway.contains(h.ore)))throw new IllegalArgumentException("25 distinct reachable wall hosts required");
        Objects.requireNonNull(items.create("beskar_scrap"),"Missing Beskar catalog scrap");
        marker=new NamespacedKey(plugin,"beskar_generation_v1");directory=plugin.getDataFolder().toPath().resolve("data/beskar-events");Files.createDirectories(directory);
        try(var files=Files.list(directory)){files.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().forEach(recovery::add);}
        Bukkit.getServicesManager().register(EventMining.class,this,plugin,ServicePriority.Normal);Bukkit.getPluginManager().registerEvents(this,plugin);
        // One small journal per tick. Receipts stay durable across future events: an old
        // chunk snapshot can still carry an older generation after a crash during cleanup.
        if(!recovery.isEmpty())recoverer=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            try{restore(new BeskarJournal(recovery.removeFirst()).load());if(recovery.isEmpty()){recoverer.cancel();recoverer=null;}}
            catch(Exception error){recoveryFailed=true;recoverer.cancel();recoverer=null;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Beskar recovery failed; event disabled",error);}
        },1,1);
    }
    private static Pos point(List<?> values,int offset){
        if(values.size()<offset+3||values.subList(offset,offset+3).stream().anyMatch(v->!(v instanceof Integer)))throw new IllegalArgumentException("Integer mine coordinates required");
        var p=new Pos((Integer)values.get(offset),(Integer)values.get(offset+1),(Integer)values.get(offset+2));
        if(Math.abs(p.x())>250||Math.abs(p.z())>250||p.y()< -64||p.y()>200)throw new IllegalArgumentException("Mine coordinate outside survey bounds");return p;
    }
    public String id(){return "beskar";}
    public String displayName(){return config.getString("display-name","Beskar Rush");}
    public boolean running(){return active||preparing;}
    public boolean miningActive(){return active;}
    public int remainingCount(){return active?25-minedCount():0;}
    public net.kyori.adventure.text.Component status(){
        String text=active?config.getString("messages.remaining","<yellow>Beskar Ore Blocks Left: <white><remaining>/25")
                :config.getString(preparing?"messages.preparing-status":"messages.inactive","<gray>Beskar Rush: Inactive");
        return MiniMessage.miniMessage().deserialize(text,net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("remaining",Integer.toString(remainingCount())));
    }
    public boolean recovering(){return recoverer!=null||recoveryFailed;}
    public int minedCount(){return state==null?0:(int)state.nodes().stream().filter(BeskarJournal.Node::mined).count();}
    public List<Host> hosts(){return hosts;}
    public List<Pos> walkway(){return walkway;}
    public Pos entrance(){return entrance;}
    private void announce(String key){Bukkit.broadcast(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(config.getString("messages."+key))));}
    public void start(){
        if(closed||running()||recovering())throw new IllegalStateException("Beskar is already running or recovering");
        preparing=true;survey=new MineSurvey(world,walkway,entrance);announce("preparing");
        preparation=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            try{if(survey.step(400)){activate();preparation.cancel();preparation=null;}}
            catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Beskar start refused: reachable deepslate hosts required",error);stop(false);}
        },1,1);
    }
    private void activate()throws Exception {
        var originals=new ArrayList<BeskarJournal.Node>();
        for(var host:hosts){
            var b=host.ore.block(world);var stand=host.stand;
            if(!survey.reached(stand)||territory.zone(b.getLocation())!=TerritoryPolicy.Zone.WARZONE||b.getType()!=Material.DEEPSLATE)throw new IllegalStateException("Changed/unreachable protected host: "+host);
            int backing=0;for(var d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})if(b.getRelative(d[0],d[1],d[2]).getType()==Material.DEEPSLATE)backing++;
            var eye=new Location(world,stand.x()+.5,survey.feet(stand)+1.62,stand.z()+.5);var aim=b.getLocation().add(.5+(stand.x()-b.getX())*.499,.5,.5+(stand.z()-b.getZ())*.499).subtract(eye).toVector();
            var hit=world.rayTraceBlocks(eye,aim.clone().normalize(),Math.min(4.5,aim.length()+.05),FluidCollisionMode.NEVER,true);
            if(backing<3||b.getRelative(0,1,0).getType()!=Material.DEEPSLATE||b.getRelative(0,-1,0).getType()!=Material.DEEPSLATE||hit==null||!b.equals(hit.getHitBlock()))throw new IllegalStateException("Host lacks deepslate backing or clear mining reach: "+host+" backing="+backing+" ray="+(hit==null?"none":hit.getHitBlock()));
            if(b.getChunk().getPersistentDataContainer().has(marker))throw new IllegalStateException("Unrecovered generation marker at "+host.ore);
            originals.add(new BeskarJournal.Node(host.ore,b.getBlockData().getAsString(),false));
        }
        state=new BeskarJournal.State(world.getKey().toString(),UUID.randomUUID().toString(),originals);
        journal=new BeskarJournal(directory.resolve(state.generation()+".yml"));journal.save(state); // WAL before any block/marker change.
        for(var node:state.nodes()){
            var b=node.position().block(world);if(b.getChunk().addPluginChunkTicket(plugin))tickets.add(b.getChunk());b.getChunk().getPersistentDataContainer().set(marker,PersistentDataType.STRING,state.generation());b.setType(Material.ANCIENT_DEBRIS,false);
        }
        preparing=false;active=true;deadline=(long)Bukkit.getCurrentTick()+maximumSeconds*20L;announce("start");
        plugin.getLogger().info("Beskar event activated25 reachable deepslate nodes; connected walk tiles="+survey.reachedCount());survey=null;
    }
    private int index(Block block){
        if(state==null||!block.getWorld().getKey().toString().equals(state.world()))return -1;
        var p=new Pos(block.getX(),block.getY(),block.getZ());for(int i=0;i<state.nodes().size();i++)if(state.nodes().get(i).position().equals(p))return i;return -1;
    }
    public boolean ownsNode(Block block){return active&&index(block)>=0;}
    private boolean validTool(Player player){var tool=player.getInventory().getItemInMainHand().getType();return player.getGameMode()==GameMode.SURVIVAL&&(tool==Material.DIAMOND_PICKAXE||tool==Material.NETHERITE_PICKAXE);}
    public boolean mayMine(Block block,Player player){
        int i=index(block);return active&&i>=0&&block.getType()==Material.ANCIENT_DEBRIS&&validTool(player)
                &&(!state.nodes().get(i).mined()||player.getUniqueId().equals(pending.get(state.nodes().get(i).position())));
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void guard(BlockBreakEvent e){int i=index(e.getBlock());if(active&&i>=0&&(state.nodes().get(i).mined()||!mayMine(e.getBlock(),e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(ownsNode(e.getBlock()))e.setCancelled(true);}
    private void mined(int index,boolean value)throws Exception {
        var copy=new ArrayList<>(state.nodes());copy.set(index,copy.get(index).mined(value));var next=new BeskarJournal.State(state.world(),state.generation(),copy);journal.save(next);state=next;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void reserve(BlockBreakEvent e){
        if(!mayMine(e.getBlock(),e.getPlayer()))return;
        var p=new Pos(e.getBlock().getX(),e.getBlock().getY(),e.getBlock().getZ());
        try{mined(index(e.getBlock()),true);pending.put(p,e.getPlayer().getUniqueId());breaks.put(e,p);e.setDropItems(false);e.setExpToDrop(0);}
        catch(Exception error){e.setCancelled(true);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot persist Beskar node consumption",error);stop(false);}
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void finishBreak(BlockBreakEvent e){
        Pos p=breaks.remove(e);if(p==null)return;pending.remove(p);
        try{if(e.isCancelled()){mined(index(e.getBlock()),false);return;}}
        catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot release cancelled Beskar consumption",error);stop(false);return;}
        String generation=state.generation();int amount=OreDropQuantity.roll(java.util.concurrent.ThreadLocalRandom.current());
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!active||!generation.equals(state.generation()))return;
            if(!p.block(world).getType().isAir()) {
                // A plugin may emit an uncancelled break event without a native removal.
                // Such a notification must neither consume supply nor deliver a reward.
                try{mined(index(p.block(world)),false);}
                catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot release uncommitted Beskar break",error);stop(false);}
                return;
            }
            var scrap=Objects.requireNonNull(items.create("beskar_scrap"));scrap.setAmount(amount);world.dropItemNaturally(p.block(world).getLocation().add(.5,.5,.5),scrap);
        });
    }
    public void tick(){if(active&&Bukkit.getCurrentTick()>=deadline)stop(true);}
    public long endingInTicks(){return active?Math.max(0,deadline-Bukkit.getCurrentTick()):Long.MAX_VALUE;}
    public boolean relevant(Player player){return player.getWorld()==world&&territory.zone(player.getLocation())==TerritoryPolicy.Zone.WARZONE;}
    private void restore(BeskarJournal.State saved){
        if(saved==null)return;var target=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString(saved.world())),"Journal world not loaded");
        var chunks=new HashMap<Long,Chunk>();
        for(var node:saved.nodes()){
            var p=node.position();var b=p.block(target);var chunk=b.getChunk();
            if(!saved.generation().equals(chunk.getPersistentDataContainer().get(marker,PersistentDataType.STRING)))continue;
            chunks.put(chunk.getChunkKey(),chunk);
            if(b.getType()==Material.ANCIENT_DEBRIS||b.getType().isAir())b.setBlockData(Bukkit.createBlockData(node.original()),false);
            else if(!b.getBlockData().getAsString().equals(node.original()))plugin.getLogger().warning("Preserved non-event edit at former Beskar node "+p);
        }
        // Marker and restored block states share one native chunk NBT transaction.
        // Durable receipts are retained so old on-disk generations remain recoverable.
        chunks.values().forEach(c->c.getPersistentDataContainer().remove(marker));
    }
    public void stop(boolean announce){
        if(preparation!=null){preparation.cancel();preparation=null;}boolean wasActive=active;active=false;preparing=false;pending.clear();breaks.clear();survey=null;
        if(state!=null)restore(state);
        // Remove only this feature's tickets, not the main plugin's unrelated tickets.
        for(var chunk:tickets)chunk.removePluginChunkTicket(plugin);tickets.clear();
        if(announce&&wasActive)announce("end");
    }
    public void close(){if(closed)return;stop(false);closed=true;if(recoverer!=null)recoverer.cancel();Bukkit.getServicesManager().unregister(EventMining.class,this);HandlerList.unregisterAll(this);}
}
