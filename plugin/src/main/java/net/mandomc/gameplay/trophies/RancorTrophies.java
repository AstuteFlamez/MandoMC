package net.mandomc.gameplay.trophies;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

/** One collision block plus one visual; inventory/world custody belongs to the durable journal. */
public final class RancorTrophies implements Listener,AutoCloseable {
    public static final String ITEM="rancor_skull";
    private static final NamespacedKey DISPLAY=new NamespacedKey("mandomc","rancor_trophy_display_v1");
    private final NamespacedKey receipts=new NamespacedKey("mandomc","trophy_receipts_v1");
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final BiPredicate<Player,Location> zoneEdits;
    private final YamlConfiguration config;
    private final TrophyJournal journal;
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    private final Set<UUID> operations=new HashSet<>(),reported=new HashSet<>();
    private final Map<UUID,UUID> players=new HashMap<>();
    private final Map<Cell,UUID> reservations=new HashMap<>();
    private final BukkitTask task;
    private boolean closed,checking;
    public RancorTrophies(JavaPlugin plugin,ItemCatalog items,BiPredicate<Player,Location> zoneEdits)throws Exception {
        this.plugin=plugin;this.items=items;this.zoneEdits=zoneEdits;
        String path="trophies/settings.yml";var file=new File(plugin.getDataFolder(),path);
        if(!file.isFile())plugin.saveResource(path,false);
        config=new YamlConfiguration();config.load(file);
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(plugin.getResource(path)),java.nio.charset.StandardCharsets.UTF_8)){
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        if(config.getInt("schema-version")!=1 || !items.ids().contains(ITEM))throw new IllegalArgumentException(path+": require schema1 and rancor_skull catalog item");
        for(String key:List.of("space","support","denied","busy","placing","placed","returned","pending","save-failed"))
            if(!config.isString("text."+key))throw new IllegalArgumentException(path+": missing text."+key);
        journal=new TrophyJournal(plugin.getDataFolder().toPath().resolve("data/trophies.yml"),config.getInt("maximum-records"));
        for(var entry:journal.entries().values())decode(entry); // Validate native item bytes before touching any world.
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getServicesManager().register(RancorTrophies.class,this,plugin,ServicePriority.Normal);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,2,20);
        for(var world:Bukkit.getWorlds()){for(var chunk:world.getLoadedChunks())cleanChunk(chunk);cleanDisplays(world.getEntities());}
    }
    public TrophyJournal journal(){return journal;}
    public boolean skull(ItemStack item){return ITEM.equals(items.identify(item));}
    /** Stable catalog IDs plus the native HEAD equipment component, never a display-name match. */
    public boolean helmet(ItemStack item){
        if(!Set.of("beskar_helmet","durasteel_helmet","plastoid_helmet","robes_helmet").contains(Objects.toString(items.identify(item),"")))return false;
        var equipment=item.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
        return equipment!=null&&equipment.slot()==EquipmentSlot.HEAD;
    }
    public boolean decoration(ItemStack item){return skull(item)||helmet(item);}
    private boolean helmet(Entry entry){return helmet(decode(entry));}
    private ItemStack decode(Entry entry){var item=ItemStack.deserializeBytes(Base64.getDecoder().decode(entry.item()));if(!decoration(item)||item.getAmount()!=1)throw new IllegalArgumentException("Invalid saved trophy item: "+entry.id());return item;}
    private void tell(Player p,String key){
        if(p==null||!p.isOnline())return;
        boolean helmet=helmet(p.getInventory().getItemInMainHand())||helmet(p.getInventory().getItemInOffHand())
                ||journal.entries().values().stream().anyMatch(e->e.owner().equals(p.getUniqueId())&&helmet(e));
        tell(p,key,helmet);
    }
    private void tell(Player p,String key,boolean helmet){
        if(p!=null&&p.isOnline())p.sendMessage(MiniMessage.miniMessage().deserialize(config.getString((helmet?"helmet-text.":"text.")+key,config.getString("text."+key))));
    }
    private <T> void io(UUID id,Callable<T> job,BiConsumer<T,Throwable> done){
        if(closed)return;
        writer.execute(()->{T result=null;Throwable error=null;try{result=job.call();}catch(Exception failure){error=failure;}var value=result;var failure=error;
            if(!closed&&plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{if(!closed){if(failure!=null&&reported.add(id))plugin.getLogger().log(java.util.logging.Level.SEVERE,"Trophy custody save failed: "+id,failure);done.accept(value,failure);}});
        });
    }
    private static Cell cell(Block b){return new Cell(b.getWorld().getKey().toString(),b.getX(),b.getY(),b.getZ());}
    private Block loaded(Cell c){var w=Bukkit.getWorld(NamespacedKey.fromString(c.world()));return w!=null&&w.isChunkLoaded(c.x()>>4,c.z()>>4)?w.getBlockAt(c.x(),c.y(),c.z()):null;}
    private NamespacedKey marker(Cell c){return new NamespacedKey("mandomc","trophy_"+(c.x()&15)+"_"+c.y()+"_"+(c.z()&15));}
    private boolean owned(Block b,Entry e){return e.id().toString().equals(b.getChunk().getPersistentDataContainer().get(marker(e.cell()),PersistentDataType.STRING));}
    private BoundingBox bounds(Block b){return new BoundingBox(b.getX()+.001,b.getY()+.001,b.getZ()+.001,b.getX()+.999,b.getY()+.999,b.getZ()+.999);}
    private boolean clear(Block b){
        if(!b.isReplaceable() || b.getY()<b.getWorld().getMinHeight() || b.getY()>=b.getWorld().getMaxHeight())return false;
        for(double x:new double[]{.001,.999})for(double z:new double[]{.001,.999})if(!b.getWorld().getWorldBorder().isInside(b.getLocation().add(x,.5,z)))return false;
        return b.getWorld().getNearbyEntities(bounds(b),e->e instanceof LivingEntity || e instanceof Vehicle || e instanceof Hanging).isEmpty();
    }
    private boolean nearby(Player p,Block b){return p!=null&&p.isOnline()&&!p.isDead()&&p.getWorld()==b.getWorld()&&p.getEyeLocation().distanceSquared(b.getLocation().add(.5,.5,.5))<=36
            && (p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.CREATIVE);}
    private Set<String> receipts(Player p){String raw=p.getPersistentDataContainer().get(receipts,PersistentDataType.STRING);return raw==null?new HashSet<>():new HashSet<>(List.of(raw.split("\n")));}
    private void receipts(Player p,Set<String> values){if(values.isEmpty())p.getPersistentDataContainer().remove(receipts);else p.getPersistentDataContainer().set(receipts,PersistentDataType.STRING,String.join("\n",new TreeSet<>(values)));}
    private boolean receipt(Player p,String value){return receipts(p).contains(value);}
    private void addReceipt(Player p,String value){var values=receipts(p);values.add(value);receipts(p,values);}
    private void release(Entry e){operations.remove(e.id());players.remove(e.owner(),e.id());reservations.remove(e.cell(),e.id());}
    /** Use the same provisional-block/event/rollback contract as native block placement. */
    private boolean authorizePlace(Player p,Block b,Block against,EquipmentSlot hand,ItemStack item){
        if(!zoneEdits.test(p,b.getLocation()))return false;
        var original=b.getState();boolean allowed;
        checking=true;
        try{b.setType(Material.BARRIER,false);var event=new BlockPlaceEvent(b,original,against,item.clone(),p,true,hand);Bukkit.getPluginManager().callEvent(event);allowed=!event.isCancelled()&&event.canBuild();}
        finally{original.update(true,false);checking=false;}
        return allowed;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event){
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK && (skull(event.getItem()) || event.getPlayer().isSneaking()&&helmet(event.getItem()))){
            // Never override an earlier region/clan cancellation. Block-place events below are the final authority.
            boolean denied=event.useInteractedBlock()==Event.Result.DENY||event.useItemInHand()==Event.Result.DENY;
            event.setCancelled(true);if(denied)return;
            if(event.getClickedBlock()==null || !Set.of(BlockFace.UP,BlockFace.DOWN,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST).contains(event.getBlockFace()))return;
            var clicked=event.getClickedBlock();var site=clicked.isReplaceable()?clicked:clicked.getRelative(event.getBlockFace());
            place(event.getPlayer(),site,event.getHand(),Face.valueOf(event.getBlockFace().name()),clicked);
        }else if(event.getAction()==Action.LEFT_CLICK_BLOCK && event.getClickedBlock()!=null){
            var e=journal.at(cell(event.getClickedBlock()));
            if(e!=null&&e.phase()==Phase.PLACED){boolean denied=event.useInteractedBlock()==Event.Result.DENY;event.setCancelled(true);if(!denied)breakTrophy(event.getPlayer(),e);}
        }
    }
    public void place(Player player,Block site,EquipmentSlot hand){place(player,site,hand,Face.UP,site.getRelative(BlockFace.DOWN));}
    public void place(Player player,Block site,EquipmentSlot hand,Face face,Block against){
        if(closed||hand==null||players.containsKey(player.getUniqueId())||reservations.containsKey(cell(site))||journal.at(cell(site))!=null){tell(player,"busy");return;}
        var held=player.getInventory().getItem(hand);if(!decoration(held)||!nearby(player,site))return;
        if(!clear(site)){tell(player,"space");return;}
        if(!zoneEdits.test(player,site.getLocation())){tell(player,"denied");return;}
        var stack=held.clone();stack.setAmount(1);
        int yaw=switch(face){case NORTH->180;case SOUTH->0;case EAST->270;case WEST->90;default->Math.floorMod(Math.round(player.getYaw()/90)*90+180,360);};
        var entry=new Entry(UUID.randomUUID(),player.getUniqueId(),cell(site),yaw,face,Base64.getEncoder().encodeToString(stack.serializeAsBytes()),Phase.PREPARED);
        operations.add(entry.id());players.put(player.getUniqueId(),entry.id());reservations.put(entry.cell(),entry.id());tell(player,"placing");
        io(entry.id(),()->{journal.reserve(entry);return null;},(v,error)->{
            if(error!=null){release(entry);tell(player,"save-failed");return;}
            var current=player.getInventory().getItem(hand);
            if(!nearby(player,site)||!decoration(current)||!current.isSimilar(stack)||!clear(site)||!zoneEdits.test(player,site.getLocation())){abort(entry);tell(player,"denied");return;}
            var remaining=current.clone();remaining.subtract(1);player.getInventory().setItem(hand,remaining.isEmpty()?null:remaining);addReceipt(player,entry.placeReceipt());
            try{player.saveData();}catch(RuntimeException failure){release(entry);tell(player,"save-failed");plugin.getLogger().log(java.util.logging.Level.SEVERE,"Trophy player snapshot failed; prepared custody retained",failure);return;}
            var placed=entry.placed();
            io(entry.id(),()->{journal.replace(entry,placed);return null;},(ignored,problem)->{
                if(problem!=null){release(entry);tell(player,"save-failed");return;}
                if(!nearby(player,site)||!clear(site)||!authorizePlace(player,site,against,hand,stack)){release(entry);returning(placed,player.getUniqueId());return;}
                install(placed,site);release(entry);tell(player,"placed");prune(player);
            });
        });
    }
    private void abort(Entry entry){io(entry.id(),()->{journal.remove(entry);return null;},(v,error)->{release(entry);if(error!=null)tell(Bukkit.getPlayer(entry.owner()),"save-failed");});}
    private void install(Entry entry,Block b){b.setType(Material.BARRIER,false);b.getChunk().getPersistentDataContainer().set(marker(entry.cell()),PersistentDataType.STRING,entry.id().toString());ensureDisplay(entry,b);}
    private void ensureDisplay(Entry entry,Block b){
        var matching=b.getWorld().getNearbyEntities(b.getLocation().add(.5,.5,.5),1,1,1).stream().filter(e->entry.id().toString().equals(e.getPersistentDataContainer().get(DISPLAY,PersistentDataType.STRING))).toList();
        if(matching.stream().anyMatch(ItemDisplay.class::isInstance)){boolean keep=false;for(var e:matching){if(e instanceof ItemDisplay display&&!keep){configureDisplay(entry,b,display);keep=true;}else e.remove();}return;}
        matching.forEach(Entity::remove);
        b.getWorld().spawn(poseLocation(entry,b),ItemDisplay.class,display->{
            display.getPersistentDataContainer().set(DISPLAY,PersistentDataType.STRING,entry.id().toString());
            display.setItemStack(decode(entry));configureDisplay(entry,b,display);
            display.setPersistent(true);display.setInvulnerable(true);display.setGravity(false);display.setShadowRadius(0);display.setViewRange(1);
        });
    }
    private Location poseLocation(Entry entry,Block b){
        var location=b.getLocation().add(.5,.5,.5);
        double inset=helmet(entry)?.25:.0625;
        switch(entry.face()){
            case UP->location.add(0,-inset,0);
            case DOWN->location.add(0,inset,0);
            case NORTH->location.add(0,0,inset);
            case SOUTH->location.add(0,0,-inset);
            case EAST->location.add(-inset,0,0);
            case WEST->location.add(inset,0,0);
        }
        location.setYaw(entry.yaw());location.setPitch(entry.face()==Face.DOWN?90:0);return location;
    }
    private void configureDisplay(Entry entry,Block b,ItemDisplay display){
        var location=poseLocation(entry,b);
        if(display.getLocation().distanceSquared(location)>1e-8 || display.getYaw()!=location.getYaw() || display.getPitch()!=location.getPitch())display.teleport(location);
        display.setItemStack(decode(entry));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        // Center the original head's Y=0..14 geometry before rotating onto a wall/ceiling.
        display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0,helmet(entry)?0:.0625f,0),new org.joml.Quaternionf(),new org.joml.Vector3f("robes_helmet".equals(items.identify(decode(entry)))?.5f:1),new org.joml.Quaternionf()));
    }
    public void breakTrophy(Player player,Entry entry){
        var b=loaded(entry.cell());if(entry.phase()!=Phase.PLACED||operations.contains(entry.id())||b==null||!owned(b,entry)||!nearby(player,b)||!zoneEdits.test(player,b.getLocation()))return;
        var event=new BlockBreakEvent(b,player);checking=true;try{Bukkit.getPluginManager().callEvent(event);}finally{checking=false;}
        if(event.isCancelled())return;
        returning(entry,player.getUniqueId());
    }
    private void returning(Entry entry,UUID recipient){
        if(operations.contains(entry.id()))return;operations.add(entry.id());var next=entry.returning(recipient);
        io(entry.id(),()->{journal.replace(entry,next);return null;},(v,error)->{release(entry);if(error==null){removeWorld(next);deliver(next);}else tell(Bukkit.getPlayer(recipient),"save-failed");});
    }
    private void removeWorld(Entry entry){
        var b=loaded(entry.cell());if(b==null)return;
        if(owned(b,entry)){if(b.getType()==Material.BARRIER)b.setType(Material.AIR,false);b.getChunk().getPersistentDataContainer().remove(marker(entry.cell()));}
        for(var e:b.getWorld().getNearbyEntities(b.getLocation().add(.5,.5,.5),1,1,1))if(entry.id().toString().equals(e.getPersistentDataContainer().get(DISPLAY,PersistentDataType.STRING)))e.remove();
    }
    private void deliver(Entry entry){
        var b=loaded(entry.cell());if((b!=null&&owned(b,entry))||operations.contains(entry.id()))return; // RETURNING anchors in unloaded chunks are inert and cleaned on load.
        var player=Bukkit.getPlayer(entry.owner());if(player==null||!player.isOnline()||player.isDead())return;
        if(!receipt(player,entry.returnReceipt())){
            var stack=decode(entry);if(!fits(player,stack)){tell(player,"pending");return;}
            player.getInventory().addItem(stack);addReceipt(player,entry.returnReceipt());
            try{player.saveData();}catch(RuntimeException failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Trophy return snapshot failed; receipt retained",failure);return;}
        }else{
            // A prior save may have thrown after the in-memory receipt was installed. Re-save it before retiring custody.
            try{player.saveData();}catch(RuntimeException failure){return;}
        }
        operations.add(entry.id());
        io(entry.id(),()->{journal.remove(entry);return null;},(v,error)->{release(entry);if(error==null){tell(player,"returned",helmet(entry));prune(player);}});
    }
    private boolean fits(Player player,ItemStack item){for(var slot:player.getInventory().getStorageContents())if(slot==null||slot.isEmpty()||slot.isSimilar(item)&&slot.getAmount()<slot.getMaxStackSize())return true;return false;}
    private void prune(Player player){var values=receipts(player);values.removeIf(value->{for(var e:journal.entries().values())if(e.owner().equals(player.getUniqueId())&&(e.phase()==Phase.PREPARED&&value.equals(e.placeReceipt())||e.phase()==Phase.RETURNING&&value.equals(e.returnReceipt())))return false;return true;});receipts(player,values);}
    public void recover(Player player){
        for(var e:List.copyOf(journal.entries().values()))if(e.owner().equals(player.getUniqueId())&&!operations.contains(e.id())){
            if(e.phase()==Phase.PREPARED){
                if(receipt(player,e.placeReceipt())){try{player.saveData();}catch(RuntimeException failure){continue;}returning(e,e.owner());}
                else{operations.add(e.id());abort(e);}
            }else if(e.phase()==Phase.RETURNING){removeWorld(e);deliver(e);}
        }
        prune(player);
    }
    private void tick(){
        if(closed)return;
        for(var e:List.copyOf(journal.entries().values()))if(!operations.contains(e.id())){
            if(e.phase()==Phase.RETURNING){removeWorld(e);deliver(e);continue;}
            var b=loaded(e.cell());if(b==null)continue;
            if(e.phase()==Phase.PLACED){
                // An absent/replaced anchor is returned, never blindly rebuilt over a reset or another player's blocks.
                if(b.getType()!=Material.BARRIER||!owned(b,e))returning(e,e.owner());else ensureDisplay(e,b);
            }
        }
        var owners=new HashSet<UUID>();for(var e:journal.entries().values())if(e.phase()==Phase.PREPARED)owners.add(e.owner());
        for(var id:owners){var p=Bukkit.getPlayer(id);if(p!=null&&p.isOnline()&&!p.isDead())recover(p);}
    }
    private void cleanDisplays(List<Entity> entities){for(var entity:entities){String id=entity.getPersistentDataContainer().get(DISPLAY,PersistentDataType.STRING);if(id==null)continue;Entry entry;try{entry=journal.entries().get(UUID.fromString(id));}catch(IllegalArgumentException invalid){entry=null;}if(entry==null||entry.phase()!=Phase.PLACED||!cell(entity.getLocation().getBlock()).equals(entry.cell()))entity.remove();}}
    private void cleanChunk(Chunk chunk){
        var pdc=chunk.getPersistentDataContainer();
        for(var key:List.copyOf(pdc.getKeys()))if(key.getNamespace().equals("mandomc")&&key.getKey().startsWith("trophy_")){
            String raw=pdc.get(key,PersistentDataType.STRING);if(raw==null)continue;Entry e;try{e=journal.entries().get(UUID.fromString(raw));}catch(IllegalArgumentException invalid){continue;}
            String[] part=key.getKey().split("_");if(part.length!=4)continue;
            try{var b=chunk.getWorld().getBlockAt((chunk.getX()<<4)+Integer.parseInt(part[1]),Integer.parseInt(part[2]),(chunk.getZ()<<4)+Integer.parseInt(part[3]));
                if(e==null||e.phase()!=Phase.PLACED||!e.cell().equals(cell(b))){if(b.getType()==Material.BARRIER)b.setType(Material.AIR,false);pdc.remove(key);}
            }catch(NumberFormatException invalid){plugin.getLogger().warning("Invalid trophy chunk marker: "+key);}
        }
    }
    @EventHandler public void chunk(ChunkLoadEvent e){cleanChunk(e.getChunk());}
    @EventHandler public void loaded(EntitiesLoadEvent e){cleanDisplays(e.getEntities());}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(plugin,()->recover(e.getPlayer()));}
    @EventHandler public void closeInventory(InventoryCloseEvent e){if(e.getPlayer() instanceof Player p)Bukkit.getScheduler().runTask(plugin,()->recover(p));}
    private boolean protectedBlock(Block b){var here=journal.at(cell(b));return here!=null&&here.phase()==Phase.PLACED;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void placeFallback(BlockPlaceEvent e){if(!checking&&(decoration(e.getItemInHand())||protectedBlock(e.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(!checking&&protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fade(BlockFadeEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityChange(EntityChangeBlockEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispense(BlockDispenseEvent e){if(skull(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void disable(PluginDisableEvent e){if(e.getPlugin()==plugin)close();}
    @Override public void close(){if(closed)return;closed=true;task.cancel();HandlerList.unregisterAll(this);Bukkit.getServicesManager().unregister(RancorTrophies.class,this);writer.shutdown();try{if(!writer.awaitTermination(20,TimeUnit.SECONDS))plugin.getLogger().severe("Trophy custody writer did not drain before shutdown");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
}
